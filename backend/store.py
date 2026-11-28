import os
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from urllib.parse import quote

PROJECT_ROOT = Path(__file__).resolve().parent.parent
API_PREFIX = "/api/v1"
SCHEMA_VERSION = 1
MEDIA_DIRECTORIES = (PROJECT_ROOT / "backend/media", PROJECT_ROOT / "3dModeling")

MONUMENT_COLUMNS = "m.*, thumbnail.path AS thumbnail, c.slug AS complex_slug, c.title AS complex_title"
MONUMENT_FROM = """
    FROM monuments m
    LEFT JOIN media_assets thumbnail ON thumbnail.id = m.thumbnail_id
    LEFT JOIN complexes c ON c.id = m.complex_id
"""
MONUMENT_SELECT = f"SELECT {MONUMENT_COLUMNS} {MONUMENT_FROM}"

COMPLEX_SELECT = """
    SELECT c.*, thumbnail.path AS thumbnail,
           (SELECT COUNT(*) FROM monuments m WHERE m.complex_id = c.id) AS monument_count
    FROM complexes c
    LEFT JOIN media_assets thumbnail ON thumbnail.id = c.thumbnail_id
"""

ROUTE_SELECT = """
    SELECT r.*, m.slug AS map_slug, m.coordinate_system,
           c.slug AS complex_slug, c.title AS complex_title
    FROM routes r
    JOIN maps m ON m.id = r.map_id
    LEFT JOIN complexes c ON c.id = r.complex_id
"""

SOURCE_COLUMNS = "s.*, association.explanation, image.path AS image, document.path AS document"
SOURCE_FROM = """
    FROM historical_sources s
    LEFT JOIN media_assets image ON image.id = s.image_id
    LEFT JOIN media_assets document ON document.id = s.document_id
"""
SOURCE_SELECT = f"SELECT {SOURCE_COLUMNS} {SOURCE_FROM}"


def database_path() -> Path:
    configured = os.getenv("SKVOZ_DB_PATH")
    return Path(configured).expanduser() if configured else PROJECT_ROOT / "backend/skvoz-veka.sqlite3"


@contextmanager
def open_database(path: Path):
    database = sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True, check_same_thread=False)
    try:
        database.row_factory = sqlite3.Row
        database.execute("PRAGMA foreign_keys = ON")
        database.execute("BEGIN")
        yield database
    finally:
        database.close()


def media_url(path: str | None) -> str | None:
    if path is None:
        return None
    relative = path.removeprefix("backend/media/")
    return "/media/" + quote(relative if "/" not in relative else path)


def media_file(path: str) -> Path | None:
    try:
        resolved = (PROJECT_ROOT / path).resolve()
        if any(resolved.is_relative_to(directory) for directory in MEDIA_DIRECTORIES) and resolved.is_file():
            return resolved
    except (OSError, ValueError, RuntimeError):
        pass
    return None


def media_asset(database: sqlite3.Connection, url_path: str) -> tuple[Path, str] | None:
    # Bare filenames use the existing /media/photo.jpg URLs.
    path = f"backend/media/{url_path}" if "/" not in url_path else url_path
    row = database.execute("SELECT mime_type FROM media_assets WHERE path = ?", (path,)).fetchone()
    if row is None and url_path != "reconstruction.jpg":
        return None
    file = media_file(path)
    if file is None:
        return None
    return file, row["mime_type"] if row else "image/jpeg"


def catalog_link(collection: str, slug: str, title: str) -> dict:
    return {"id": slug, "title": title, "card": f"{API_PREFIX}/{collection}/{quote(slug, safe='')}"}


def complex_link(row: sqlite3.Row) -> dict | None:
    if row["complex_slug"] is None:
        return None
    return catalog_link("complexes", row["complex_slug"], row["complex_title"])


def monument_summary(row: sqlite3.Row) -> dict:
    return {
        **catalog_link("monuments", row["slug"], row["title"]),
        "subtitle": row["subtitle"] or "",
        "thumbnail": media_url(row["thumbnail"]),
        "complex": complex_link(row),
    }


def monument_row(database: sqlite3.Connection, slug: str) -> sqlite3.Row | None:
    return database.execute(MONUMENT_SELECT + " WHERE m.slug = ?", (slug,)).fetchone()


def monument_card(database: sqlite3.Connection, row: sqlite3.Row) -> dict:
    summary = monument_summary(row)
    buildings = [
        {
            "title": building["title"],
            "builtYear": building["built_year"],
            "builtYearEnd": building["built_year_end"],
            "dismantledYear": building["dismantled_year"],
        }
        for building in database.execute(
            "SELECT * FROM monument_buildings WHERE monument_id = ? ORDER BY sort_order, id", (row["id"],)
        )
    ]
    primary = buildings[0] if buildings else {}
    card = summary["card"]
    return {
        **summary,
        "ensemble": row["complex_title"] or "",
        "description": row["description"],
        "address": row["address"] or "",
        "hint": row["hint"],
        "builtYear": primary.get("builtYear"),
        "builtYearEnd": primary.get("builtYearEnd"),
        "dismantledYear": primary.get("dismantledYear"),
        "buildings": buildings,
        "comparison": f"{card}/comparison",
        "source": f"{card}/source",
        "sources": f"{card}/sources",
        "ar": f"{card}/ar",
    }


def source_data(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"],
        "title": row["title"],
        "image": media_url(row["image"]),
        "caption": row["caption"] or "",
        "credit": row["credit"] or "",
        "collection": row["collection"] or "",
        "explanation": row["explanation"] or "",
        "documentUrl": media_url(row["document"]),
        "documentPage": row["document_page"],
        "referenceUrl": row["reference_url"],
    }


def monument_sources(database: sqlite3.Connection, monument_id: int) -> list[dict]:
    query = SOURCE_SELECT + """
        JOIN monument_sources association ON association.source_id = s.id
        WHERE association.monument_id = ? ORDER BY association.sort_order, s.id
    """
    return [source_data(row) for row in database.execute(query, (monument_id,))]


def monument_views(database: sqlite3.Connection, monument_id: int) -> list[sqlite3.Row]:
    return database.execute("""
        SELECT v.*, image.path AS image, model.path AS model
        FROM monument_views v
        LEFT JOIN media_assets image ON image.id = v.image_id
        LEFT JOIN media_assets model ON model.id = v.model_id
        WHERE v.monument_id = ? ORDER BY v.sort_order, v.id
    """, (monument_id,)).fetchall()


def comparison(database: sqlite3.Connection, monument: sqlite3.Row) -> dict:
    view_sources = {}
    query = f"SELECT association.view_id, {SOURCE_COLUMNS} {SOURCE_FROM}" + """
        JOIN view_sources association ON association.source_id = s.id
        JOIN monument_views v ON v.id = association.view_id
        WHERE v.monument_id = ? ORDER BY s.id
    """
    for row in database.execute(query, (monument["id"],)):
        view_sources.setdefault(row["view_id"], []).append(source_data(row))
    card = catalog_link("monuments", monument["slug"], monument["title"])["card"]
    return {
        "title": "Сравните облик",
        "views": [
            {
                "id": view["slug"],
                "label": view["label"],
                "title": view["title"],
                "description": view["description"],
                "image": media_url(view["image"]),
                "modelUrl": media_url(view["model"]),
                "sources": view_sources.get(view["id"], []),
            }
            for view in monument_views(database, monument["id"])
        ],
        "source": f"{card}/source",
        "ar": f"{card}/ar",
    }


def ar_guide(database: sqlite3.Connection, monument: sqlite3.Row) -> dict:
    config = database.execute("""
        SELECT a.title, a.instruction, sign.path AS sign, target.path AS target
        FROM ar_configs a
        LEFT JOIN media_assets sign ON sign.id = a.sign_preview_id
        LEFT JOIN media_assets target ON target.id = a.tracking_target_id
        WHERE a.monument_id = ?
    """, (monument["id"],)).fetchone()
    models = [
        {"viewId": view["slug"], "label": view["label"], "modelUrl": media_url(view["model"])}
        for view in monument_views(database, monument["id"])
        if view["model"] and media_file(view["model"])
    ]
    target = config["target"] if config else None
    tracking_url = media_url(target) if target and media_file(target) else None
    card = catalog_link("monuments", monument["slug"], monument["title"])["card"]
    return {
        "title": config["title"] if config else "Сравнение эпох",
        "instruction": config["instruction"] if config else "Для этого памятника доступен просмотр без камеры.",
        "signPreview": media_url(config["sign"]) if config else None,
        "trackingTargetUrl": tracking_url,
        "modelUrl": models[0]["modelUrl"] if models else None,
        "arAvailable": bool(tracking_url and models),
        "models": models,
        "fallback": {"label": "Смотреть без камеры", "url": f"{card}/comparison"},
    }


def complex_summary(row: sqlite3.Row) -> dict:
    return {
        **catalog_link("complexes", row["slug"], row["title"]),
        "thumbnail": media_url(row["thumbnail"]),
        "monumentCount": row["monument_count"],
    }


def complexes(database: sqlite3.Connection) -> list[dict]:
    return [complex_summary(row) for row in database.execute(COMPLEX_SELECT + " ORDER BY c.id")]


def complex_row(database: sqlite3.Connection, slug: str) -> sqlite3.Row | None:
    return database.execute(COMPLEX_SELECT + " WHERE c.slug = ?", (slug,)).fetchone()


def complex_card(database: sqlite3.Connection, row: sqlite3.Row) -> dict:
    params = (row["id"],)
    monuments = MONUMENT_SELECT + " WHERE m.complex_id = ? ORDER BY m.sort_order, m.id"
    sources = SOURCE_SELECT + """
        JOIN complex_sources association ON association.source_id = s.id
        WHERE association.complex_id = ? ORDER BY association.sort_order, s.id
    """
    routes = ROUTE_SELECT + " WHERE r.complex_id = ? ORDER BY r.sort_order, r.id"
    summary = complex_summary(row)
    return {
        **summary,
        "description": row["description"],
        "map": f"{summary['card']}/map",
        "monuments": [monument_summary(item) for item in database.execute(monuments, params)],
        "sources": [source_data(item) for item in database.execute(sources, params)],
        "routes": [route_summary(item) for item in database.execute(routes, params)],
    }


def route_summary(row: sqlite3.Row) -> dict:
    return {
        "id": row["slug"],
        "title": row["title"],
        "description": row["description"],
        "url": f"{API_PREFIX}/routes/{quote(row['slug'], safe='')}",
    }


def route_row(database: sqlite3.Connection, slug: str) -> sqlite3.Row | None:
    return database.execute(ROUTE_SELECT + " WHERE r.slug = ?", (slug,)).fetchone()


def route_detail(database: sqlite3.Connection, row: sqlite3.Row) -> dict:
    params = (row["id"],)
    points = database.execute(
        "SELECT x, y FROM route_points WHERE route_id = ? ORDER BY position", params
    ).fetchall()
    stops = database.execute("""
        SELECT s.point_position, s.label, p.x, p.y, m.slug, m.title
        FROM route_stops s
        JOIN route_points p ON p.route_id = s.route_id AND p.position = s.point_position
        LEFT JOIN monuments m ON m.id = s.monument_id
        WHERE s.route_id = ? ORDER BY s.point_position
    """, params).fetchall()
    return {
        **route_summary(row),
        "label": row["start_label"] or row["title"],
        "map": row["map_slug"],
        "coordinateSystem": row["coordinate_system"],
        "complex": complex_link(row),
        "points": [tuple(point) for point in points],
        "stops": [
            {
                "position": stop["point_position"],
                "label": stop["label"],
                "x": stop["x"],
                "y": stop["y"],
                "monument": catalog_link("monuments", stop["slug"], stop["title"]) if stop["slug"] else None,
            }
            for stop in stops
        ],
    }


def guide_map(database: sqlite3.Connection, complex_: sqlite3.Row | None = None) -> dict | None:
    if complex_ is None:
        map_row = database.execute("SELECT * FROM maps ORDER BY id LIMIT 1").fetchone()
    else:
        map_row = database.execute("SELECT * FROM maps WHERE id = ?", (complex_["map_id"],)).fetchone()
    if map_row is None:
        return None

    # Keep the same monument columns on cards and map markers.
    marker_query = f"SELECT marker.x, marker.y, {MONUMENT_COLUMNS} {MONUMENT_FROM}" + """
        JOIN map_markers marker ON marker.monument_id = m.id
        WHERE marker.map_id = ?
    """
    route_query = ROUTE_SELECT + " WHERE r.map_id = ?"
    params = (map_row["id"],)
    if complex_ is not None:
        marker_query += " AND m.complex_id = ?"
        route_query += " AND r.complex_id = ?"
        params += (complex_["id"],)
    markers = database.execute(marker_query + " ORDER BY m.sort_order, m.id", params).fetchall()
    route = database.execute(route_query + " ORDER BY r.sort_order, r.id LIMIT 1", params).fetchone()
    return {
        "id": map_row["slug"],
        "title": map_row["title"],
        "city": map_row["city"],
        "coordinateSystem": map_row["coordinate_system"],
        "markers": [{**monument_summary(marker), "x": marker["x"], "y": marker["y"]} for marker in markers],
        "route": route_detail(database, route) if route else {"label": "", "points": [], "stops": []},
        "complex": catalog_link("complexes", complex_["slug"], complex_["title"]) if complex_ else None,
    }
