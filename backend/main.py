"""Read-only API for the Skvoz Veka mobile guide. Run with python main.py."""
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
CHURCH = "/api/v1/monuments/tserkov-lazarya"
SCREENS = {
    "/api/v1/map": "map",
    CHURCH: "card",
    f"{CHURCH}/comparison": "comparison",
    f"{CHURCH}/source": "source",
    f"{CHURCH}/ar": "ar",
}


def load_resources():
    catalog = json.loads((ROOT / "catalog.json").read_text(encoding="utf-8"))
    resources = {
        url: ("application/json; charset=utf-8", json.dumps(catalog[key], ensure_ascii=False).encode())
        for url, key in SCREENS.items()
    }
    for name in ("today.jpg", "reconstruction.jpg", "drawing.jpg", "sign.jpg"):
        resources[f"/media/{name}"] = ("image/jpeg", (ROOT / "media" / name).read_bytes())
    return resources


class GuideHandler(BaseHTTPRequestHandler):
    def respond(self):
        resource = self.server.resources.get(urlsplit(self.path).path)
        if resource is None:
            code, mime, body = 404, "application/json", b'{"error":"not_found"}'
        elif self.command == "OPTIONS":
            code, mime, body = 204, None, b""
        elif self.command in ("GET", "HEAD"):
            mime, body = resource
            code = 200
        else:
            code, mime, body = 405, "application/json", b'{"error":"method_not_allowed"}'

        self.send_response(code)
        self.send_header("Access-Control-Allow-Origin", "*")
        if code in (204, 405):
            self.send_header("Allow", "GET, HEAD, OPTIONS")
            self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        if mime:
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD" and body:
            self.wfile.write(body)

    do_GET = do_HEAD = do_OPTIONS = respond
    do_POST = do_PUT = do_PATCH = do_DELETE = respond
    do_TRACE = do_CONNECT = respond


if __name__ == "__main__":
    resources = load_resources()
    address = (os.getenv("HOST", "127.0.0.1"), int(os.getenv("PORT", "8080")))
    with ThreadingHTTPServer(address, GuideHandler) as server:
        server.resources = resources
        print(f"http://{address[0]}:{server.server_port}/api/v1/map", flush=True)
        server.serve_forever()
