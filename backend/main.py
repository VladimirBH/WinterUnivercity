"""FastAPI server for the Skvoz Veka guide. Run with python main.py."""

import os
import sqlite3
from collections.abc import Iterator
from contextlib import asynccontextmanager
from typing import Annotated, TypeVar

import uvicorn
from fastapi import APIRouter, Depends, FastAPI, HTTPException, Request
from fastapi.exception_handlers import http_exception_handler
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

if __package__:
    from . import schemas, store
else:
    import schemas
    import store


@asynccontextmanager
async def lifespan(app: FastAPI):
    path = store.database_path().resolve()
    try:
        with store.open_database(path) as database:
            version = database.execute("PRAGMA user_version").fetchone()[0]
            if version != store.SCHEMA_VERSION:
                raise RuntimeError(
                    f"Unsupported SQLite schema in {path}: expected {store.SCHEMA_VERSION}, got {version}"
                )
            if database.execute("SELECT id FROM maps LIMIT 1").fetchone() is None:
                raise RuntimeError(f"SQLite catalog {path} has no maps")
    except sqlite3.Error as error:
        raise RuntimeError(f"Could not read SQLite catalog {path}: {error}") from error
    app.state.database_path = path
    yield


app = FastAPI(title="Сквозь века", version="1.0", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET", "HEAD"])
api = APIRouter(prefix=store.API_PREFIX)


def get_database(request: Request) -> Iterator[sqlite3.Connection]:
    with store.open_database(request.app.state.database_path) as database:
        yield database


Database = Annotated[sqlite3.Connection, Depends(get_database)]
T = TypeVar("T")


def require_result(result: T | None) -> T:
    if result is None:
        raise HTTPException(status_code=404)
    return result


@app.exception_handler(StarletteHTTPException)
async def catalog_http_error(request: Request, error: StarletteHTTPException):
    codes = {404: "not_found", 405: "method_not_allowed"}
    if error.status_code in codes:
        return JSONResponse(
            {"error": codes[error.status_code]}, status_code=error.status_code, headers=error.headers
        )
    return await http_exception_handler(request, error)


@api.head("/map", include_in_schema=False)
@api.get("/map", response_model=schemas.GuideMap, tags=["Карта"])
def guide_map(database: Database):
    return require_result(store.guide_map(database))


@api.head("/complexes", include_in_schema=False)
@api.get("/complexes", response_model=list[schemas.ComplexSummary], tags=["Комплексы"])
def list_complexes(database: Database):
    return store.complexes(database)


@api.head("/complexes/{slug}/map", include_in_schema=False)
@api.get("/complexes/{slug}/map", response_model=schemas.GuideMap, tags=["Комплексы"])
def complex_map(slug: str, database: Database):
    complex_ = require_result(store.complex_row(database, slug))
    return require_result(store.guide_map(database, complex_))


@api.head("/complexes/{slug}", include_in_schema=False)
@api.get("/complexes/{slug}", response_model=schemas.ComplexCard, tags=["Комплексы"])
def complex_card(slug: str, database: Database):
    return store.complex_card(database, require_result(store.complex_row(database, slug)))


@api.head("/monuments/{slug}", include_in_schema=False)
@api.get("/monuments/{slug}", response_model=schemas.MonumentCard, tags=["Памятники"])
def monument_card(slug: str, database: Database):
    return store.monument_card(database, require_result(store.monument_row(database, slug)))


@api.head("/monuments/{slug}/comparison", include_in_schema=False)
@api.get("/monuments/{slug}/comparison", response_model=schemas.Comparison, tags=["Памятники"])
def comparison(slug: str, database: Database):
    return store.comparison(database, require_result(store.monument_row(database, slug)))


@api.head("/monuments/{slug}/sources", include_in_schema=False)
@api.get("/monuments/{slug}/sources", response_model=list[schemas.HistoricalSource], tags=["Памятники"])
def monument_sources(slug: str, database: Database):
    monument = require_result(store.monument_row(database, slug))
    return store.monument_sources(database, monument["id"])


@api.head("/monuments/{slug}/source", include_in_schema=False)
@api.get("/monuments/{slug}/source", response_model=schemas.HistoricalSource, tags=["Памятники"])
def primary_source(slug: str, database: Database):
    sources = monument_sources(slug, database)
    return require_result(sources[0] if sources else None)


@api.head("/monuments/{slug}/ar", include_in_schema=False)
@api.get("/monuments/{slug}/ar", response_model=schemas.ArGuide, tags=["Памятники"])
def ar_guide(slug: str, database: Database):
    return store.ar_guide(database, require_result(store.monument_row(database, slug)))


@api.head("/routes/{slug}", include_in_schema=False)
@api.get("/routes/{slug}", response_model=schemas.RouteDetail, tags=["Маршруты"])
def route_detail(slug: str, database: Database):
    return store.route_detail(database, require_result(store.route_row(database, slug)))


@app.head("/media/{path:path}", include_in_schema=False)
@app.get("/media/{path:path}", response_class=FileResponse, tags=["Медиа"])
def media(path: str, database: Database):
    file, mime_type = require_result(store.media_asset(database, path))
    return FileResponse(file, media_type=mime_type, headers={"X-Content-Type-Options": "nosniff"})


app.include_router(api)


if __name__ == "__main__":
    uvicorn.run(app, host=os.getenv("HOST", "127.0.0.1"), port=int(os.getenv("PORT", "8080")))
