import { useDeferredValue, useEffect, useEffectEvent, useRef, useState } from "react";
import {
  ArrowDownAZ,
  ChevronDown,
  Clapperboard,
  Film,
  LoaderCircle,
  MapPin,
  Menu,
  Plus,
  RotateCcw,
  Search,
  Star,
  UserRound,
  X,
} from "lucide-react";

const coverStyles = ["cover-sage", "cover-coral", "cover-blue", "cover-citrus", "cover-mint"];

function directorName(movie) {
  const director = movie.director;
  if (director && typeof director === "object") {
    return [director.firstName, director.lastName].filter(Boolean).join(" ").trim();
  }
  return "";
}

function creditLine(movie) {
  const director = directorName(movie);
  if (director) return `Directed by ${director}`;
  const firstActor = Array.isArray(movie.actors) ? movie.actors[0] : null;
  if (firstActor && typeof firstActor === "object") {
    const name = [firstActor.firstName, firstActor.lastName].filter(Boolean).join(" ").trim();
    if (name) return `Featuring ${name}`;
  }
  return "Recently added to your collection";
}

function sortFilms(films, order) {
  return [...films].sort((left, right) => {
    if (order === "newest") return Number(right.year || 0) - Number(left.year || 0);
    if (order === "oldest") return Number(left.year || 0) - Number(right.year || 0);
    if (order === "rating") return Number(right.rating || 0) - Number(left.rating || 0);
    return String(left.name || "").localeCompare(String(right.name || ""));
  });
}

function MovieCard({ movie, index }) {
  const genres = Array.isArray(movie.genres) ? movie.genres.filter(Boolean).join(" / ") : "";
  const rating = Number(movie.rating);

  return (
    <article className={`movie-card ${coverStyles[index % coverStyles.length]}`}>
      <div className="movie-art" aria-hidden="true">
        <span className="movie-index">FILE {String(index + 1).padStart(3, "0")}</span>
        <Clapperboard className="art-mark" strokeWidth={1} />
        <p className="movie-art-title">{movie.name || "Untitled film"}</p>
      </div>
      <div className="movie-content">
        <div className="movie-meta">
          <span className="movie-year">{movie.year || "YEAR UNKNOWN"}</span>
          {rating > 0 ? (
            <span className="movie-rating"><Star size={12} fill="currentColor" /> {rating} / 5</span>
          ) : <span className="movie-rating unrated">NOT RATED</span>}
        </div>
        <h3 className="movie-title">{movie.name || "Untitled film"}</h3>
        <p className="movie-genres">{genres || "UNCATEGORIZED"}</p>
        <p className="movie-synopsis">{movie.synopsis || "No synopsis has been added yet."}</p>
        <p className="movie-credit">{creditLine(movie)}</p>
      </div>
    </article>
  );
}

function FeaturedFilm({ movie, status, error, onAdd, onBrowse }) {
  const actors = Array.isArray(movie?.actors) ? movie.actors : [];
  const director = movie ? directorName(movie) : "";
  const genres = Array.isArray(movie?.genres) ? movie.genres.filter(Boolean) : [];

  return (
    <section className="spotlight" aria-label="Featured film">
      <div className="spotlight-poster" aria-hidden="true">
        <div className="poster-atmosphere" />
        <span className="poster-kicker">SCREENROOM · FEATURE 001</span>
        <Clapperboard className="poster-symbol" strokeWidth={1} />
        <div className="poster-caption">
          <span>AN ARCHIVE SELECTION</span>
          <strong>{movie?.name || "Stories on screen"}</strong>
          <small>{movie?.year || "YOUR PRIVATE CINEMA"}</small>
        </div>
        <span className="poster-border" />
      </div>

      <div className="spotlight-info">
        <p className="spotlight-eyebrow">{movie ? "FEATURED FROM YOUR COLLECTION" : "YOUR PRIVATE CINEMA"}</p>
        <h1>{movie?.name || (status === "error" ? "Your collection awaits." : "Stories on screen.")}</h1>
        {movie ? (
          <>
            <div className="genre-list">
              {genres.length ? genres.slice(0, 4).map((genre) => <span className="genre-chip" key={genre}>{genre}</span>) : <span className="genre-chip">Uncategorized</span>}
            </div>
            <div className="film-facts">
              <span><b>{movie.year || "—"}</b><small>RELEASE</small></span>
              {movie.ageLimit ? <span><b>{movie.ageLimit}+</b><small>AGE GUIDE</small></span> : null}
              {movie.rating ? <span><b><Star size={13} fill="currentColor" /> {movie.rating}/5</b><small>YOUR RATING</small></span> : null}
            </div>
            <p className="spotlight-synopsis">{movie.synopsis || "A story waiting to be discovered."}</p>
          </>
        ) : (
          <p className="spotlight-synopsis">{status === "error" ? error : "A considered collection of films, ready for your next movie night."}</p>
        )}
        <div className="spotlight-actions">
          <button className="button button-accent" type="button" onClick={movie ? onBrowse : onAdd}>
            {movie ? "BROWSE COLLECTION" : "ADD YOUR FIRST FILM"}
            <ChevronDown size={15} />
          </button>
          {movie && <button className="button button-outline" type="button" onClick={onAdd}><Plus size={15} /> ADD A FILM</button>}
        </div>
      </div>

      <aside className="spotlight-credits" aria-label="Film credits">
        <div className="credit-panel">
          <span>DIRECTOR</span>
          <strong>{director || "Not listed"}</strong>
          <Clapperboard size={14} aria-hidden="true" />
        </div>
        <div className="credit-panel">
          <span>STARRING</span>
          <strong>{actors.length ? actors.slice(0, 4).map((actor) => [actor.firstName, actor.lastName].filter(Boolean).join(" ").trim()).filter(Boolean).join(" · ") : "Cast details unavailable"}</strong>
          <UserRound size={14} aria-hidden="true" />
        </div>
        <div className="archive-state">
          <span className={status === "error" ? "state-indicator offline" : "state-indicator"} />
          <span>{status === "error" ? "SERVICE UNAVAILABLE" : "SAVED TO YOUR LIBRARY"}</span>
        </div>
      </aside>
    </section>
  );
}

function AddFilmDialog({ dialogRef, onCreated }) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function submitFilm(event) {
    event.preventDefault();
    const formElement = event.currentTarget;
    setSaving(true);
    setError("");
    const form = new FormData(event.currentTarget);
    const year = String(form.get("year") || "").trim();
    const rating = String(form.get("rating") || "").trim();
    const movie = {
      name: String(form.get("name") || "").trim(),
      ...(year ? { year: Number(year) } : {}),
      ...(rating ? { rating: Number(rating) } : {}),
      genres: String(form.get("genres") || "").split(",").map((genre) => genre.trim()).filter(Boolean),
      synopsis: String(form.get("synopsis") || "").trim(),
    };

    try {
      const response = await fetch("/api/movies", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(movie),
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.error || `The service returned ${response.status}.`);
      formElement.reset();
      dialogRef.current?.close("saved");
      onCreated(movie.name);
    } catch (requestError) {
      setError(requestError.message || "Could not save this film. Try again.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <dialog
      className="add-dialog"
      ref={dialogRef}
      aria-labelledby="dialog-title"
      onClick={(event) => {
        if (event.target === dialogRef.current) dialogRef.current?.close();
      }}
    >
      <form onSubmit={submitFilm}>
        <div className="dialog-heading">
          <div><p className="eyebrow">EXPAND THE ARCHIVE</p><h2 id="dialog-title">Add a film</h2></div>
          <button className="icon-button close-button" type="button" onClick={() => dialogRef.current?.close()} aria-label="Close form">
            <X size={18} />
          </button>
        </div>
        <label className="form-field form-field-wide">
          <span>Film title <b>*</b></span>
          <input name="name" required maxLength="160" autoFocus placeholder="e.g. Arrival" />
        </label>
        <div className="form-row">
          <label className="form-field">
            <span>Year</span>
            <input name="year" type="number" min="1888" max="2100" placeholder="2024" />
          </label>
          <label className="form-field">
            <span>Your rating</span>
            <select name="rating" defaultValue="">
              <option value="">Not rated</option>
              <option value="5">5 · Essential</option>
              <option value="4">4 · Excellent</option>
              <option value="3">3 · Good</option>
              <option value="2">2 · Fair</option>
              <option value="1">1 · Skip</option>
            </select>
          </label>
        </div>
        <label className="form-field form-field-wide">
          <span>Genres <small>Separate with commas</small></span>
          <input name="genres" maxLength="160" placeholder="Drama, Sci-fi" />
        </label>
        <label className="form-field form-field-wide">
          <span>Synopsis</span>
          <textarea name="synopsis" rows="3" maxLength="1200" placeholder="What is this film about?" />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="dialog-actions">
          <button className="button button-quiet" type="button" onClick={() => dialogRef.current?.close()}>Cancel</button>
          <button className="button button-dark" type="submit" disabled={saving}>
            {saving && <LoaderCircle className="spin" size={15} />}
            {saving ? "Saving..." : "Save film"}
          </button>
        </div>
      </form>
    </dialog>
  );
}

export default function App() {
  const [search, setSearch] = useState("");
  const deferredSearch = useDeferredValue(search.trim());
  const [sortOrder, setSortOrder] = useState("title");
  const [movies, setMovies] = useState([]);
  const [status, setStatus] = useState("loading");
  const [error, setError] = useState("");
  const [reloadVersion, setReloadVersion] = useState(0);
  const [toast, setToast] = useState("");
  const [toastVersion, setToastVersion] = useState(0);
  const dialogRef = useRef(null);
  const searchRef = useRef(null);
  const staleSearch = deferredSearch !== search.trim();

  useEffect(() => {
    const controller = new AbortController();
    const timeout = window.setTimeout(async () => {
      setStatus("loading");
      setError("");
      const endpoint = deferredSearch ? `/api/movies?q=${encodeURIComponent(deferredSearch)}` : "/api/movies";
      try {
        const response = await fetch(endpoint, {
          signal: controller.signal,
          headers: { Accept: "application/json" },
        });
        if (!response.ok) throw new Error(`The service returned ${response.status}.`);
        const result = await response.json();
        setMovies(Array.isArray(result) ? result : []);
        setStatus("ready");
      } catch (requestError) {
        if (requestError.name === "AbortError") return;
        setError(requestError.message || "Check your connection and try again.");
        setStatus("error");
      }
    }, 200);

    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [deferredSearch, reloadVersion]);

  useEffect(() => {
    if (!toast) return undefined;
    const timeout = window.setTimeout(() => setToast(""), 3200);
    return () => window.clearTimeout(timeout);
  }, [toast, toastVersion]);

  const handleShortcut = useEffectEvent((event) => {
    if (event.key === "/" && !dialogRef.current?.open && document.activeElement !== searchRef.current) {
      event.preventDefault();
      searchRef.current?.focus();
    }
  });

  useEffect(() => {
    const onKeyDown = (event) => handleShortcut(event);
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, []);

  const visibleMovies = sortFilms(movies, sortOrder);
  const isSearching = Boolean(search.trim());
  const title = status === "error" ? "Collection unavailable" : isSearching ? "Search results" : "All films";

  function openDialog() {
    dialogRef.current?.showModal();
  }

  function showAddedToast(name) {
    setToast(`${name} was added to your collection.`);
    setToastVersion((version) => version + 1);
    setSearch("");
    setReloadVersion((version) => version + 1);
  }

  function reloadMovies() {
    setReloadVersion((version) => version + 1);
  }

  function browseCollection() {
    document.querySelector("#collection")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  return (
    <div className="cinema-backdrop">
      <div className="cinema-screen">
        <header className="screen-nav">
          <button className="icon-button nav-menu" type="button" onClick={browseCollection} aria-label="Browse your collection"><Menu size={18} /></button>
          <a className="wordmark" href="/" aria-label="Screenroom home">WATCHER<span>.</span></a>
          <nav className="screen-links" aria-label="Main navigation">
            <a href="#collection">My collection</a>
            <span className="nav-divider" />
            <span className="nav-context">PERSONAL CINEMA</span>
          </nav>
          <div className="screen-nav-end">
            <span className={`database-state ${status === "error" ? "database-state-offline" : ""}`}>
              <span /> {status === "error" ? "SERVICE OFFLINE" : status === "loading" ? "CONNECTING" : "LIBRARY ONLINE"}
            </span>
            <button className="button button-nav" type="button" onClick={openDialog}><Plus size={15} /> Add film</button>
          </div>
        </header>

        <main className="cinema-content">
          <FeaturedFilm
            movie={movies[0] || null}
            status={status}
            error={error}
            onAdd={openDialog}
            onBrowse={browseCollection}
          />

          <section className="library-section" id="collection" aria-labelledby="library-title">
            <div className="library-heading">
              <div>
                <p className="screen-eyebrow">THE PRIVATE ARCHIVE</p>
                <h2 id="library-title">Choose your next film</h2>
              </div>
              <div className="library-total">
                <strong>{String(movies.length).padStart(2, "0")}</strong>
                <span>FILMS<br />SAVED</span>
              </div>
            </div>

            <div className="library-toolbar" aria-label="Search and sort the collection">
              <label className="search-field">
                <Search size={16} aria-hidden="true" />
                <span className="visually-hidden">Search films</span>
                <input
                  ref={searchRef}
                  type="search"
                  placeholder="Search films, cast, genres..."
                  autoComplete="off"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
                {search ? (
                  <button className="clear-search" type="button" onClick={() => setSearch("")} aria-label="Clear search"><X size={14} /></button>
                ) : <kbd>/</kbd>}
              </label>
              <div className="sort-control">
                <ArrowDownAZ size={15} aria-hidden="true" />
                <label className="visually-hidden" htmlFor="sort-order">Sort films</label>
                <select id="sort-order" value={sortOrder} onChange={(event) => setSortOrder(event.target.value)}>
                  <option value="title">TITLE</option>
                  <option value="newest">NEWEST</option>
                  <option value="oldest">OLDEST</option>
                  <option value="rating">RATING</option>
                </select>
              </div>
              <span className="collection-type"><MapPin size={14} /> LOCAL LIBRARY</span>
            </div>

            <div className="collection-feedback">
              <h3>{status === "error" ? "Library connection" : isSearching ? "Search results" : "All films"}</h3>
              <span aria-live="polite">
                {status === "loading" || staleSearch ? "Updating..." : status === "error" ? error : `${visibleMovies.length} ${visibleMovies.length === 1 ? "film" : "films"}`}
              </span>
            </div>

            {status === "error" && (
              <div className="notice notice-error" role="alert">
                <div><strong>The library is unavailable.</strong><span>{error}</span></div>
                <button className="button button-outline" type="button" onClick={reloadMovies}>Retry <RotateCcw size={14} /></button>
              </div>
            )}

            {status === "loading" && movies.length === 0 && (
              <div className="loading-grid" aria-label="Loading movies" aria-busy="true">
                {[0, 1, 2].map((item) => <div className="loading-card" key={item}><div /><span /><span /></div>)}
              </div>
            )}

            {status !== "error" && status !== "loading" && visibleMovies.length === 0 && (
              <section className="empty-state">
                <Film size={28} strokeWidth={1.2} />
                <h2>{isSearching ? "No films found" : "The first reel is yours"}</h2>
                <p>{isSearching ? "Try another search, or add this title to your collection." : "Add a favourite and begin building your film index."}</p>
                <button className="button button-accent" type="button" onClick={openDialog}><Plus size={15} /> ADD A FILM</button>
              </section>
            )}

            {visibleMovies.length > 0 && (
              <div className={`movie-grid ${staleSearch ? "is-stale" : ""}`} aria-live="polite" aria-busy={status === "loading"}>
                {visibleMovies.map((movie, index) => <MovieCard key={`${movie.name}-${movie.year}-${index}`} movie={movie} index={index} />)}
              </div>
            )}
          </section>
        </main>

        <footer className="screen-footer">
          <span>WATCHER<span>.</span> · SCREENROOM {new Date().getFullYear()}</span>
          <span>YOUR FILMS, ALL IN ONE PLACE</span>
        </footer>
      </div>
      <AddFilmDialog dialogRef={dialogRef} onCreated={showAddedToast} />
      {toast && <div className="toast" role="status" aria-live="polite">{toast}</div>}
    </div>
  );
}
