from pydantic import BaseModel


class CatalogLink(BaseModel):
    id: str
    title: str
    card: str


class MonumentSummary(CatalogLink):
    subtitle: str
    thumbnail: str | None
    complex: CatalogLink | None


class MapMarker(MonumentSummary):
    x: float
    y: float
    latitude: float | None = None
    longitude: float | None = None


class Building(BaseModel):
    title: str
    builtYear: int | None
    builtYearEnd: int | None
    dismantledYear: int | None


class MonumentCard(MonumentSummary, Building):
    ensemble: str
    description: str
    address: str
    hint: str | None
    buildings: list[Building]
    comparison: str
    source: str
    sources: str
    ar: str


class HistoricalSource(BaseModel):
    id: int
    title: str
    image: str | None
    caption: str
    credit: str
    collection: str
    explanation: str
    documentUrl: str | None
    documentPage: int | None
    referenceUrl: str | None


class MonumentView(BaseModel):
    id: str
    label: str
    title: str
    description: str
    image: str | None
    modelUrl: str | None
    sources: list[HistoricalSource]


class Comparison(BaseModel):
    title: str
    views: list[MonumentView]
    source: str
    ar: str


class ArModel(BaseModel):
    viewId: str
    label: str
    modelUrl: str


class ArFallback(BaseModel):
    label: str
    url: str


class ArGuide(BaseModel):
    title: str
    instruction: str
    signPreview: str | None
    trackingTargetUrl: str | None
    modelUrl: str | None
    arAvailable: bool
    models: list[ArModel]
    fallback: ArFallback


class RouteSummary(BaseModel):
    id: str
    title: str
    description: str | None
    url: str


class RouteStop(BaseModel):
    position: int
    label: str
    x: float
    y: float
    monument: CatalogLink | None


class RouteLine(BaseModel):
    label: str
    points: list[tuple[float, float]]
    stops: list[RouteStop]


class RouteDetail(RouteSummary, RouteLine):
    map: str
    coordinateSystem: str
    complex: CatalogLink | None


class GuideMap(BaseModel):
    id: str
    title: str
    city: str
    coordinateSystem: str
    markers: list[MapMarker]
    route: RouteDetail | RouteLine
    complex: CatalogLink | None


class ComplexSummary(CatalogLink):
    thumbnail: str | None
    monumentCount: int


class ComplexCard(ComplexSummary):
    description: str
    map: str
    monuments: list[MonumentSummary]
    sources: list[HistoricalSource]
    routes: list[RouteSummary]
