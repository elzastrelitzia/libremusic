import json
import os
import shutil
import sys
import urllib.request
import zipfile


# yt-dlp and its JS-challenge companion. The app ships both, so keep them in step.
PACKAGES = ("yt-dlp", "yt-dlp-ejs")

# Chaquopy has no `python` executable and modern pip shells out to one, so pip
# cannot run in-app (chaquopy#623). Instead fetch the pure-python wheel straight
# from PyPI and unzip it onto sys.path.
# use wheels only; if either package ever ships a compiled wheel this
# needs a per-ABI pick instead of the single "-none-any.whl" match.
WHEEL_SUFFIX = "-none-any.whl"


def download(quickjs_bin: str, video_id: str) -> str:
    import yt_dlp  # imported per call so an in-app upgrade takes effect

    opts = {
        'format': 'bestaudio',
        'js_runtimes': { "quickjs": { "path": quickjs_bin } }
    }

    return json.dumps(yt_dlp.YoutubeDL(opts).extract_info(video_id, download=False), indent=4)


def upgrade(package_name="yt-dlp"):
    """Install the newest pure-python wheels into $HOME and activate them.

    Returns True on success; raises on failure so the Java caller can report it.
    """
    dest_root = os.path.join(os.environ["HOME"], "ytdlp")
    os.makedirs(dest_root, exist_ok=True)
    print(f"[yt-dlp] upgrade requested: {package_name} -> {dest_root}")

    installed = []
    for project in PACKAGES:
        result = _fetch(project, dest_root)
        if result is not None:
            installed.append(result)

    _activate(dest_root, installed)

    # Drop the loaded module so the next `import yt_dlp` picks up the new one.
    for name in [n for n in list(sys.modules) if n == "yt_dlp" or n.startswith("yt_dlp.")]:
        del sys.modules[name]
    import yt_dlp
    print(f"[yt-dlp] active: {yt_dlp.version.__version__} from {yt_dlp.__file__}")
    return True


def _resolve_wheel(project):
    with urllib.request.urlopen(f"https://pypi.org/pypi/{project}/json", timeout=30) as resp:
        info = json.load(resp)
    candidates = [
        (f["upload_time_iso_8601"], version, f["url"])
        for version, files in info["releases"].items()
        for f in files
        if f["filename"].endswith(WHEEL_SUFFIX) and not f.get("yanked")
    ]
    if not candidates:
        raise RuntimeError(f"no pure-python wheel for {project}")
    _, version, url = max(candidates)
    return version, url


def _fetch(project, dest_root):
    try:
        version, url = _resolve_wheel(project)
    except Exception as e:
        if project == PACKAGES[0]:
            print(f"[yt-dlp] cannot resolve {project}: {e}")
            raise
        print(f"[yt-dlp] optional {project} skipped: {e}")
        return _existing(project, dest_root)

    dest = os.path.join(dest_root, project, version)
    if os.path.isdir(dest):
        print(f"[yt-dlp] {project} {version} already present")
        return project, version, dest

    print(f"[yt-dlp] downloading {project} {version}: {url}")
    tmp = dest + ".tmp"
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp)
    wheel = os.path.join(tmp, "package.whl")
    with urllib.request.urlopen(url, timeout=120) as resp, open(wheel, "wb") as out:
        shutil.copyfileobj(resp, out)
    with zipfile.ZipFile(wheel) as z:
        z.extractall(tmp)
    os.remove(wheel)
    os.replace(tmp, dest)
    print(f"[yt-dlp] {project} {version} installed")
    return project, version, dest


def _existing(project, dest_root):
    root = os.path.join(dest_root, project)
    if not os.path.isdir(root):
        return None
    entries = os.listdir(root)
    if not entries:
        return None
    newest = max(entries, key=lambda e: os.path.getmtime(os.path.join(root, e)))
    return project, newest, os.path.join(root, newest)


def _activate(dest_root, installed):
    for project, version, _ in installed:
        root = os.path.join(dest_root, project)
        for entry in os.listdir(root):
            if entry != version:
                shutil.rmtree(os.path.join(root, entry), ignore_errors=True)
    for _, _, dest in reversed(installed):
        while dest in sys.path:
            sys.path.remove(dest)
        sys.path.insert(0, dest)
    print(f"[yt-dlp] sys.path: {[d for _, _, d in installed]}")


def warm_up():
    import yt_dlp
    print("Python Music Engine: Warmed up and ready.")
