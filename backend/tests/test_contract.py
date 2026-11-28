"""HTTP and SQLite contracts for the two-church demo catalog."""

import json
import os
import socket
import sqlite3
import threading
import time
import unittest
from contextlib import closing
from http.client import HTTPConnection
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

import uvicorn

from backend import store
from backend.main import app, lifespan


API = "/api/v1"
DATABASE = store.PROJECT_ROOT / "backend" / "skvoz-veka.sqlite3"
LAZARUS = "tserkov-lazarya"
PETER_AND_PAUL = "tserkov-petra-i-pavla-v-kozhevnikah"
COMPLEX = "tserkvi-na-bredova-zverinoy"
MONUMENTS = {LAZARUS, PETER_AND_PAUL}
DATE_FIELDS = ("builtYear", "builtYearEnd", "dismantledYear")
PETER_AND_PAUL_PHOTO = "/media/peter-paul-today.jpg"


class CatalogDatabaseTest(unittest.TestCase):
    def test_catalog_integrity_and_read_only_connection(self):
        with store.open_database(DATABASE) as database:
            self.assertEqual(database.execute("PRAGMA user_version").fetchone()[0], store.SCHEMA_VERSION)
            self.assertEqual(database.execute("PRAGMA foreign_keys").fetchone()[0], 1)
            self.assertEqual([row[0] for row in database.execute("PRAGMA integrity_check")], ["ok"])
            self.assertEqual(database.execute("PRAGMA foreign_key_check").fetchall(), [])
            with self.assertRaises(sqlite3.OperationalError) as failure:
                database.execute("CREATE TABLE contract_read_only_probe (id INTEGER)")
            self.assertEqual(failure.exception.sqlite_errorcode, sqlite3.SQLITE_READONLY)


class CatalogStartupTest(unittest.IsolatedAsyncioTestCase):
    async def test_rejects_catalog_without_a_map(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "empty.sqlite3"
            with closing(sqlite3.connect(path)) as database:
                database.execute(f"PRAGMA user_version = {store.SCHEMA_VERSION}")
                database.execute("CREATE TABLE maps (id INTEGER PRIMARY KEY)")
            with patch.dict(os.environ, {"SKVOZ_DB_PATH": str(path)}):
                with self.assertRaisesRegex(RuntimeError, "has no maps"):
                    async with lifespan(app):
                        self.fail("Startup accepted a catalog without a map")


class CatalogHttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        listener = socket.socket()
        cls.addClassCleanup(listener.close)
        listener.bind(("127.0.0.1", 0))
        cls.port = listener.getsockname()[1]

        server = uvicorn.Server(uvicorn.Config(app, lifespan="on", log_level="error", access_log=False))
        thread = threading.Thread(target=server.run, kwargs={"sockets": [listener]}, daemon=True)

        def stop_server():
            server.should_exit = True
            thread.join(timeout=5)
            if thread.is_alive():
                raise RuntimeError("Contract test server did not shut down")

        with patch.dict(os.environ, {"SKVOZ_DB_PATH": str(DATABASE)}):
            thread.start()
            cls.addClassCleanup(stop_server)
            deadline = time.monotonic() + 5
            while not server.started and thread.is_alive() and time.monotonic() < deadline:
                time.sleep(0.01)
            if not server.started:
                raise RuntimeError("Contract test server did not start")

    def request(self, method, path, headers=None):
        connection = HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            connection.request(method, path, headers=headers or {})
            response = connection.getresponse()
            return response.status, response.headers, response.read()
        finally:
            connection.close()

    def get_json(self, path):
        status, headers, body = self.request("GET", path)
        self.assertEqual(status, 200, path)
        self.assertEqual(headers["Content-Type"], "application/json", path)
        return json.loads(body)

    def assert_media(self, path, mime_type, signature):
        status, headers, body = self.request("GET", path)
        self.assertEqual(status, 200, path)
        self.assertEqual(headers["Content-Type"], mime_type, path)
        self.assertEqual(headers["X-Content-Type-Options"], "nosniff", path)
        self.assertGreater(len(body), len(signature), path)
        self.assertEqual(body[: len(signature)], signature, path)
        self.assert_head(path, headers, len(body))

    def assert_head(self, path, get_headers, expected_length):
        status, headers, body = self.request("HEAD", path)
        self.assertEqual(status, 200, path)
        self.assertEqual(headers["Content-Type"], get_headers["Content-Type"], path)
        self.assertEqual(int(headers["Content-Length"]), expected_length, path)
        self.assertEqual(body, b"", path)

    def test_map_complex_and_route_link_both_monuments(self):
        guide_map = self.get_json(f"{API}/map")
        markers = {marker["id"]: marker for marker in guide_map["markers"]}
        self.assertEqual(set(markers), MONUMENTS)
        self.assertEqual(len(guide_map["markers"]), len(markers))
        self.assertEqual(markers[PETER_AND_PAUL]["thumbnail"], PETER_AND_PAUL_PHOTO)
        self.assertIsNone(guide_map["complex"])
        self.assertEqual(guide_map["coordinateSystem"], "schematic-normalized-top-left")

        summaries = self.get_json(f"{API}/complexes")
        summary = next(item for item in summaries if item["id"] == COMPLEX)
        complex_card = self.get_json(summary["card"])
        self.assertEqual(summary, {key: complex_card[key] for key in summary})
        self.assertEqual(summary["monumentCount"], len(MONUMENTS))
        self.assertEqual({entry["id"] for entry in complex_card["monuments"]}, MONUMENTS)
        complex_link = {key: summary[key] for key in ("id", "title", "card")}
        complex_map = self.get_json(complex_card["map"])
        self.assertEqual(complex_map["complex"], complex_link)
        self.assertEqual(complex_map["id"], guide_map["id"])
        self.assertEqual(complex_map["markers"], guide_map["markers"])

        route_summary = next(route for route in complex_card["routes"] if route["id"] == "dve-tserkvi")
        route = self.get_json(route_summary["url"])
        self.assertEqual(route_summary, {key: route[key] for key in route_summary})
        self.assertEqual(guide_map["route"], route)
        self.assertEqual(complex_map["route"], route)
        self.assertEqual(route["complex"], complex_link)
        self.assertEqual(route["map"], guide_map["id"])
        self.assertEqual(route["coordinateSystem"], guide_map["coordinateSystem"])
        self.assertEqual([stop["position"] for stop in route["stops"]], [0, 1, 2])
        self.assertEqual([stop["monument"]["id"] for stop in route["stops"][1:]], [PETER_AND_PAUL, LAZARUS])
        self.assertIsNone(route["stops"][0]["monument"])
        for stop in route["stops"]:
            with self.subTest(stop=stop["label"]):
                self.assertEqual(route["points"][stop["position"]], [stop["x"], stop["y"]])
                self.assertTrue(all(0 <= stop[axis] <= 1 for axis in ("x", "y")))
                if stop["monument"] is not None:
                    marker = markers[stop["monument"]["id"]]
                    self.assertEqual(stop["monument"], {key: marker[key] for key in ("id", "title", "card")})
                    self.assertEqual((stop["x"], stop["y"]), (marker["x"], marker["y"]))

        for entry in complex_card["monuments"]:
            with self.subTest(monument=entry["id"]):
                card = self.get_json(entry["card"])
                marker = markers[entry["id"]]
                self.assertEqual(card["complex"], complex_link)
                self.assertEqual(entry, {key: card[key] for key in entry})
                self.assertEqual(entry, {key: marker[key] for key in entry})

    def test_cards_comparisons_and_historical_sources(self):
        expected = {
            LAZARUS: ([(1461, None, 1859), (1861, 1862, 1935)], ["today", "xv", "xix"]),
            PETER_AND_PAUL: ([(1406, None, None)], ["today", "xix"]),
        }
        complex_card = self.get_json(f"{API}/complexes/{COMPLEX}")
        shared_sources = {source["id"]: source for source in complex_card["sources"]}
        self.assertTrue(shared_sources)

        for monument, (dates, views) in expected.items():
            with self.subTest(monument=monument):
                card_path = f"{API}/monuments/{monument}"
                card = self.get_json(card_path)
                self.assertEqual(card["id"], monument)
                self.assertEqual(card["card"], card_path)
                self.assertEqual(tuple(card[field] for field in DATE_FIELDS), dates[0])
                self.assertEqual(
                    [tuple(building[field] for field in DATE_FIELDS) for building in card["buildings"]], dates
                )
                comparison = self.get_json(card["comparison"])
                self.assertEqual([view["id"] for view in comparison["views"]], views)
                self.assertEqual(comparison["source"], card["source"])
                self.assertEqual(comparison["ar"], card["ar"])

                sources = self.get_json(card["sources"])
                self.assertTrue(sources)
                self.assertEqual(self.get_json(card["source"]), sources[0])
                source_by_id = {source["id"]: source for source in sources}
                self.assertEqual(len(source_by_id), len(sources))
                self.assertTrue(source_by_id.keys() & shared_sources.keys())
                self.assertTrue(comparison["views"][0]["sources"])
                for view in comparison["views"]:
                    for source in view["sources"]:
                        linked_source = source_by_id[source["id"]]
                        self.assertTrue(source["explanation"])
                        self.assertEqual(
                            {key: value for key, value in source.items() if key != "explanation"},
                            {key: value for key, value in linked_source.items() if key != "explanation"},
                        )
                for source in sources:
                    if source["documentUrl"]:
                        self.assertGreater(source["documentPage"], 0)
                    else:
                        self.assertIsNone(source["documentPage"])
                        self.assertTrue(source["referenceUrl"])

    def test_ar_and_no_camera_fallback(self):
        for monument in sorted(MONUMENTS):
            with self.subTest(monument=monument):
                card = self.get_json(f"{API}/monuments/{monument}")
                ar = self.get_json(card["ar"])
                self.assertIs(ar["arAvailable"], False)
                self.assertIsNone(ar["trackingTargetUrl"])
                self.assertTrue(ar["fallback"]["label"])
                self.assertEqual(ar["fallback"]["url"], card["comparison"])
                comparison = self.get_json(ar["fallback"]["url"])
                expected_models = [
                    {"viewId": view["id"], "label": view["label"], "modelUrl": view["modelUrl"]}
                    for view in comparison["views"]
                    if view["modelUrl"] is not None
                ]
                self.assertTrue(expected_models)
                self.assertEqual(ar["models"], expected_models)
                self.assertEqual(ar["modelUrl"], expected_models[0]["modelUrl"])
                for model in ar["models"]:
                    self.assert_media(model["modelUrl"], "model/gltf-binary", b"glTF")

    def test_legacy_jpegs_and_linked_media(self):
        for filename in ("today.jpg", "drawing.jpg", "reconstruction.jpg", "sign.jpg"):
            with self.subTest(filename=filename):
                self.assert_media(f"/media/{filename}", "image/jpeg", bytes.fromhex("ffd8ff"))

        complex_card = self.get_json(f"{API}/complexes/{COMPLEX}")
        document = next(source["documentUrl"] for source in complex_card["sources"] if source["documentUrl"])
        self.assert_media(document, "application/pdf", b"%PDF-")

    def test_peter_and_paul_current_view_photo_has_attribution(self):
        card = self.get_json(f"{API}/monuments/{PETER_AND_PAUL}")
        self.assertEqual(card["thumbnail"], PETER_AND_PAUL_PHOTO)
        comparison = self.get_json(card["comparison"])
        current_view = next(view for view in comparison["views"] if view["id"] == "today")
        self.assertEqual(current_view["image"], PETER_AND_PAUL_PHOTO)
        photo = next(source for source in current_view["sources"] if source["image"] == PETER_AND_PAUL_PHOTO)
        self.assertEqual(photo["credit"], "Vadim Zhivotovsky · CC BY 3.0")
        self.assertTrue(photo["referenceUrl"].startswith("https://commons.wikimedia.org/wiki/File:"))
        self.assertIn("https://creativecommons.org/licenses/by/3.0/", photo["explanation"])
        self.assert_media(PETER_AND_PAUL_PHOTO, "image/jpeg", bytes.fromhex("ffd8ff"))

    def test_http_methods_errors_and_cors(self):
        origin = "https://guide.example"
        map_path = f"{API}/map"
        status, headers, body = self.request("GET", map_path, {"Origin": origin})
        self.assertEqual(status, 200)
        self.assertEqual(headers["Access-Control-Allow-Origin"], "*")
        self.assert_head(map_path, headers, len(body))

        for path in (f"{API}/unknown-endpoint", f"{API}/monuments/no-such-monument", "/media/missing.jpg"):
            with self.subTest(path=path):
                status, _, body = self.request("GET", path)
                self.assertEqual((status, json.loads(body)), (404, {"error": "not_found"}))
                status, _, body = self.request("HEAD", path)
                self.assertEqual((status, body), (404, b""))

        for method in ("POST", "OPTIONS"):
            with self.subTest(method=method):
                status, headers, body = self.request(method, map_path)
                self.assertEqual((status, json.loads(body)), (405, {"error": "method_not_allowed"}))
                self.assertIn("GET", headers["Allow"])

        status, headers, _ = self.request(
            "OPTIONS", map_path, {"Origin": origin, "Access-Control-Request-Method": "GET"}
        )
        self.assertEqual(status, 200)
        self.assertEqual(headers["Access-Control-Allow-Origin"], "*")
        self.assertEqual(set(headers["Access-Control-Allow-Methods"].split(", ")), {"GET", "HEAD"})


if __name__ == "__main__":
    unittest.main()
