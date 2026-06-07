#!/usr/bin/env python3
from __future__ import annotations

import json
import mimetypes
import os
import secrets
import traceback
import uuid
from dataclasses import dataclass
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from typing import Any, Dict, Optional, Tuple
from urllib import request as urlrequest
from urllib import error as urlerror
from urllib.parse import quote, urlparse, parse_qs

APP_NAME = "RKh BPB Wizard"
BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "static"
WORKER_FILE = BASE_DIR / "worker" / "worker.js"
CONFIG_FILE = BASE_DIR / "rkh_bpb_profiles.json"
CF_API_BASE = "https://api.cloudflare.com/client/v4"
LOCAL_TOKEN = secrets.token_urlsafe(32)
COOKIE_NAME = "rkh_bpb_local_token"


def dumps(data: Any) -> bytes:
    return json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def safe_name(value: str, fallback: str = "nova-panel") -> str:
    cleaned = "".join(ch.lower() if ch.isalnum() else "-" for ch in (value or "").strip())
    while "--" in cleaned:
        cleaned = cleaned.replace("--", "-")
    return cleaned.strip("-") or fallback


def read_json(handler: BaseHTTPRequestHandler) -> Dict[str, Any]:
    length = int(handler.headers.get("Content-Length") or "0")
    raw = handler.rfile.read(length) if length else b"{}"
    if not raw:
        return {}
    return json.loads(raw.decode("utf-8"))


def rand_secret(prefix: str = "") -> str:
    return prefix + secrets.token_urlsafe(24).replace("-", "x").replace("_", "y")


BANNED_RESOURCE_WORDS = {"bpb", "rkh", "worker"}
WORDS_A = ["sunny", "nova", "swift", "neon", "atlas", "orbit", "pixel", "rocket", "falcon", "crystal", "rainbow", "mango", "coral", "luna", "pearl", "turbo"]
WORDS_B = ["panel", "bridge", "node", "core", "wave", "path", "gate", "proxy", "stack", "vault", "spark", "portal", "cloud", "river", "garden", "comet"]
STORE_WORDS = ["vault", "store", "cache", "locker", "garden", "stash", "bucket", "shelf"]


def contains_banned_resource_word(name: str) -> bool:
    parts = [p for p in safe_name(name, "").split("-") if p]
    return any(part in BANNED_RESOURCE_WORDS for part in parts)


def random_resource_name(max_len: int = 55) -> str:
    for _ in range(50):
        stem = f"{secrets.choice(WORDS_A)}-{secrets.choice(WORDS_B)}-{secrets.choice(WORDS_A)}-{secrets.token_hex(3)}"
        stem = safe_name(stem, "nova-panel")[:max_len].strip("-")
        if stem and not contains_banned_resource_word(stem):
            return stem
    return f"nova-panel-{secrets.token_hex(4)}"[:max_len]


def remove_banned_resource_words(name: str) -> str:
    parts = [p for p in safe_name(name, "").split("-") if p and p not in BANNED_RESOURCE_WORDS]
    return "-".join(parts)


def random_suggestion() -> Dict[str, str]:
    worker_name = random_resource_name(55)
    kv_namespace = safe_name(f"{worker_name}-{secrets.choice(STORE_WORDS)}", "nova-panel-vault")[:60].strip("-")
    kv_namespace = remove_banned_resource_words(kv_namespace)[:60].strip("-") or f"nova-panel-{secrets.token_hex(4)}-vault"[:60]
    return {
        "worker_name": worker_name,
        "kv_namespace": kv_namespace,
    }


def user_resource_name(value: str, *, max_len: int, fallback: str) -> str:
    cleaned = remove_banned_resource_words(value)[:max_len].strip("-")
    if not cleaned or contains_banned_resource_word(cleaned):
        cleaned = fallback[:max_len].strip("-")
    return cleaned or random_resource_name(max_len)


def random_sub_path() -> str:
    return safe_name("sub-" + secrets.token_urlsafe(9), "sub-" + secrets.token_hex(6))[:64]


def load_store() -> Dict[str, Any]:
    if not CONFIG_FILE.exists():
        return {"profiles": [], "deployments": []}
    try:
        data = json.loads(CONFIG_FILE.read_text("utf-8"))
        if not isinstance(data, dict):
            return {"profiles": [], "deployments": []}
        data.setdefault("profiles", [])
        data.setdefault("deployments", [])
        return data
    except Exception:
        return {"profiles": [], "deployments": []}


def save_store(data: Dict[str, Any]) -> None:
    CONFIG_FILE.write_text(json.dumps(data, ensure_ascii=False, indent=2), "utf-8")


def public_profile(profile: Dict[str, Any]) -> Dict[str, Any]:
    # Never send saved secrets back to the browser. They stay only in the local JSON file
    # and are used server-side when the selected profile calls Cloudflare.
    out = {k: v for k, v in profile.items() if k not in ("api_token", "global_key")}
    for key in ("api_token", "global_key"):
        value = profile.get(key) or ""
        out[key + "_masked"] = ("•" * 8 + value[-4:]) if value else ""
        out[key + "_saved"] = bool(value)
    return out


def get_profile(profile_id: str) -> Optional[Dict[str, Any]]:
    for profile in load_store().get("profiles", []):
        if profile.get("id") == profile_id:
            return profile
    return None


def merge_profile_payload(payload: Dict[str, Any]) -> Dict[str, Any]:
    profile_id = (payload.get("profile_id") or "").strip()
    if profile_id:
        profile = get_profile(profile_id)
        if not profile:
            raise ValueError("Selected profile was not found")
        merged = dict(profile)
        for k, v in payload.items():
            if v not in (None, ""):
                merged[k] = v
        return merged
    return payload


@dataclass
class Auth:
    mode: str
    api_token: str = ""
    global_key: str = ""
    email: str = ""

    @classmethod
    def from_payload(cls, payload: Dict[str, Any]) -> "Auth":
        payload = merge_profile_payload(payload)
        mode = (payload.get("auth_mode") or "api_token").strip()
        if mode == "api_token":
            token = (payload.get("api_token") or "").strip()
            if not token:
                raise ValueError("API Token is required")
            return cls(mode="api_token", api_token=token)
        if mode == "global_key":
            email = (payload.get("email") or "").strip()
            key = (payload.get("global_key") or "").strip()
            if not email or not key:
                raise ValueError("Email and Global API Key are required")
            return cls(mode="global_key", email=email, global_key=key)
        raise ValueError("auth_mode must be api_token or global_key")

    def headers(self) -> Dict[str, str]:
        if self.mode == "api_token":
            return {"Authorization": f"Bearer {self.api_token}"}
        return {"X-Auth-Email": self.email, "X-Auth-Key": self.global_key}


class CFError(RuntimeError):
    def __init__(self, message: str, status: Optional[int] = None, payload: Optional[Any] = None):
        super().__init__(message)
        self.status = status
        self.payload = payload


class CFClient:
    def __init__(self, auth: Auth, proxy_url: str = "", timeout: int = 70):
        self.auth = auth
        self.timeout = timeout
        self.proxy_url = (proxy_url or "").strip()
        if self.proxy_url.lower().startswith(("socks://", "socks4://", "socks5://", "socks5h://")):
            raise ValueError("SOCKS proxy needs extra packages. Use an HTTP/HTTPS proxy URL, or turn on VPN system-wide.")
        if self.proxy_url:
            self.opener = urlrequest.build_opener(urlrequest.ProxyHandler({"http": self.proxy_url, "https": self.proxy_url}))
        else:
            # Default opener respects normal system networking and environment proxy settings.
            # If a VPN is system-wide, Cloudflare API traffic goes through it.
            self.opener = urlrequest.build_opener()

    def request(self, method: str, path: str, *, json_body=None, data: Optional[bytes] = None, content_type: Optional[str] = None, extra_headers=None) -> Any:
        headers = dict(self.auth.headers())
        headers.update({"Accept": "application/json", "User-Agent": "wizard-local/1.7"})
        if extra_headers:
            headers.update(extra_headers)
        if json_body is not None:
            data = dumps(json_body)
            headers["Content-Type"] = "application/json"
        elif content_type:
            headers["Content-Type"] = content_type
        req = urlrequest.Request(CF_API_BASE + path, data=data, headers=headers, method=method.upper())
        try:
            with self.opener.open(req, timeout=self.timeout) as resp:
                raw = resp.read()
                payload = json.loads(raw.decode("utf-8")) if raw else {"success": True, "result": None}
                if isinstance(payload, dict) and payload.get("success") is False:
                    raise CFError(format_cf_error(payload), status=resp.status, payload=payload)
                return payload
        except urlerror.HTTPError as exc:
            raw = exc.read()
            try:
                payload = json.loads(raw.decode("utf-8")) if raw else None
            except Exception:
                payload = raw.decode("utf-8", "replace") if raw else None
            # Cloudflare DELETE may return 404 if already deleted. Let cleanup handle that separately when needed.
            raise CFError(format_cf_error(payload) if payload else f"Cloudflare HTTP {exc.code}", status=exc.code, payload=payload)
        except urlerror.URLError as exc:
            raise CFError("Network error while connecting to Cloudflare API. If Cloudflare is filtered, turn on a system-wide VPN or set an HTTP/HTTPS Proxy URL in the profile. Details: " + str(exc.reason))


def format_cf_error(payload: Any) -> str:
    if isinstance(payload, dict):
        errors = payload.get("errors") or []
        msgs = []
        for err in errors:
            if isinstance(err, dict):
                code = err.get("code")
                msg = err.get("message")
                msgs.append(f"[{code}] {msg}" if code else str(msg))
            else:
                msgs.append(str(err))
        if msgs:
            return "Cloudflare error: " + " | ".join(msgs)
        if payload.get("message"):
            return str(payload["message"])
    return str(payload)


def build_multipart(metadata: Dict[str, Any], worker_code: bytes) -> Tuple[bytes, str]:
    boundary = "----wiz" + secrets.token_hex(16)
    nl = "\r\n"
    parts: list[bytes] = []
    def add(name: str, value: bytes, ctype: str, filename: Optional[str] = None):
        disp = f'Content-Disposition: form-data; name="{name}"'
        if filename:
            disp += f'; filename="{filename}"'
        parts.append((f"--{boundary}{nl}{disp}{nl}Content-Type: {ctype}{nl}{nl}").encode("utf-8") + value + nl.encode("utf-8"))
    add("metadata", dumps(metadata), "application/json")
    add("worker.js", worker_code, "application/javascript+module", "worker.js")
    parts.append(f"--{boundary}--{nl}".encode("utf-8"))
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def list_namespaces(cf: CFClient, account_id: str) -> list[dict[str, Any]]:
    out = []
    page = 1
    while True:
        payload = cf.request("GET", f"/accounts/{quote(account_id)}/storage/kv/namespaces?per_page=100&page={page}")
        out.extend(payload.get("result") or [])
        info = payload.get("result_info") or {}
        if page >= int(info.get("total_pages") or 1):
            return out
        page += 1


def find_kv_by_title(cf: CFClient, account_id: str, title: str) -> Optional[Dict[str, Any]]:
    title = (title or "").strip()
    if not title:
        return None
    for ns in list_namespaces(cf, account_id):
        if ns.get("title") == title:
            return ns
    return None


def get_or_create_kv(cf: CFClient, account_id: str, title: str) -> Dict[str, Any]:
    existing = find_kv_by_title(cf, account_id, title)
    if existing:
        return {"id": existing.get("id"), "title": title, "reused": True}
    payload = cf.request("POST", f"/accounts/{quote(account_id)}/storage/kv/namespaces", json_body={"title": title})
    res = payload.get("result") or {}
    return {"id": res.get("id"), "title": res.get("title") or title, "reused": False}


def get_or_set_account_subdomain(cf: CFClient, account_id: str, desired: str) -> Optional[str]:
    try:
        p = cf.request("GET", f"/accounts/{quote(account_id)}/workers/subdomain")
        res = p.get("result") or {}
        sub = res.get("subdomain") or res.get("name")
        if sub:
            return sub
    except Exception:
        pass
    desired = safe_name(desired or f"nova-{secrets.token_hex(4)}", "nova-panel")
    last = None
    for method in ("PUT", "POST", "PATCH"):
        try:
            p = cf.request(method, f"/accounts/{quote(account_id)}/workers/subdomain", json_body={"subdomain": desired})
            res = p.get("result") or {}
            return res.get("subdomain") or desired
        except Exception as exc:
            last = exc
    raise CFError(f"Could not create workers.dev account subdomain. Set it once in Cloudflare dashboard or enter another subdomain. Last error: {last}")


def enable_script_subdomain(cf: CFClient, account_id: str, worker_name: str) -> bool:
    last = None
    for method in ("POST", "PUT", "PATCH"):
        try:
            cf.request(method, f"/accounts/{quote(account_id)}/workers/scripts/{quote(worker_name)}/subdomain", json_body={"enabled": True})
            return True
        except Exception as exc:
            last = exc
    raise CFError(f"Worker uploaded, but could not enable workers.dev route. Last error: {last}")


def deploy(payload: Dict[str, Any]) -> Dict[str, Any]:
    payload = merge_profile_payload(payload)
    auth = Auth.from_payload(payload)
    cf = CFClient(auth, payload.get("proxy_url") or "")
    account_id = (payload.get("account_id") or "").strip()
    suggestion = random_suggestion()
    worker_name = user_resource_name(payload.get("worker_name") or "", max_len=55, fallback=suggestion["worker_name"])
    kv_title = user_resource_name(payload.get("kv_namespace") or "", max_len=60, fallback=f"{worker_name}-{secrets.choice(STORE_WORDS)}")
    desired_subdomain = safe_name(payload.get("workers_dev_subdomain") or "")
    if not account_id:
        raise ValueError("account_id is required")
    if not WORKER_FILE.exists():
        raise FileNotFoundError(f"worker.js not found: {WORKER_FILE}")

    account_subdomain = get_or_set_account_subdomain(cf, account_id, desired_subdomain)
    kv = get_or_create_kv(cf, account_id, kv_title)
    kv_id = kv.get("id")
    if not kv_id:
        raise CFError("Could not get KV namespace ID")

    gen_uuid = str(uuid.uuid4())
    tr_pass = rand_secret("tr_")
    panel_password = rand_secret("panel_")
    sub_path = random_sub_path()  # Always random for every deploy.

    cf.request("PUT", f"/accounts/{quote(account_id)}/storage/kv/namespaces/{quote(kv_id)}/values/pwd", data=panel_password.encode("utf-8"), content_type="text/plain; charset=utf-8")

    metadata = {
        "main_module": "worker.js",
        "compatibility_date": "2025-01-01",
        "bindings": [
            {"type": "kv_namespace", "name": "kv", "namespace_id": kv_id},
            {"type": "plain_text", "name": "UUID", "text": gen_uuid},
            {"type": "plain_text", "name": "TR_PASS", "text": tr_pass},
            {"type": "plain_text", "name": "SUB_PATH", "text": sub_path},
        ],
    }
    body, ctype = build_multipart(metadata, WORKER_FILE.read_bytes())
    cf.request("PUT", f"/accounts/{quote(account_id)}/workers/scripts/{quote(worker_name)}", data=body, content_type=ctype)
    enable_script_subdomain(cf, account_id, worker_name)

    worker_url = f"https://{worker_name}.{account_subdomain}.workers.dev" if account_subdomain else ""
    result = {
        "id": secrets.token_hex(8),
        "profile_id": payload.get("profile_id") or "",
        "profile_name": payload.get("name") or "",
        "account_id": account_id,
        "worker_name": worker_name,
        "kv_namespace": kv,
        "worker_url": worker_url,
        "panel_url": worker_url + "/panel" if worker_url else "",
        "subscription_url": worker_url + "/" + sub_path if worker_url else "",
        "uuid": gen_uuid,
        "tr_pass": tr_pass,
        "panel_password": panel_password,
        "sub_path": sub_path,
        "account_subdomain": account_subdomain,
        "status": "active",
    }

    store = load_store()
    store.setdefault("deployments", []).insert(0, result)
    # Keep deploy history small.
    store["deployments"] = store["deployments"][:50]
    save_store(store)
    return result


def cleanup(payload: Dict[str, Any]) -> Dict[str, Any]:
    payload = merge_profile_payload(payload)
    auth = Auth.from_payload(payload)
    cf = CFClient(auth, payload.get("proxy_url") or "")
    account_id = (payload.get("account_id") or "").strip()
    worker_name = safe_name(payload.get("worker_name") or "")
    kv_id = (payload.get("kv_id") or payload.get("namespace_id") or "").strip()
    kv_title = safe_name(payload.get("kv_namespace") or "", "") if payload.get("kv_namespace") else ""
    deploy_id = (payload.get("deployment_id") or "").strip()
    if not account_id:
        raise ValueError("account_id is required")
    if not worker_name and not kv_id and not kv_title:
        raise ValueError("Nothing to delete. Provide worker name and/or KV namespace.")

    deleted = {"worker": False, "kv": False, "notes": []}
    if worker_name:
        try:
            cf.request("DELETE", f"/accounts/{quote(account_id)}/workers/scripts/{quote(worker_name)}")
            deleted["worker"] = True
        except CFError as exc:
            if exc.status == 404:
                deleted["notes"].append("Worker was already deleted or not found.")
            else:
                raise
    if not kv_id and kv_title:
        ns = find_kv_by_title(cf, account_id, kv_title)
        if ns:
            kv_id = ns.get("id") or ""
        else:
            deleted["notes"].append("KV namespace was already deleted or not found by title.")
    if kv_id:
        try:
            cf.request("DELETE", f"/accounts/{quote(account_id)}/storage/kv/namespaces/{quote(kv_id)}")
            deleted["kv"] = True
        except CFError as exc:
            if exc.status == 404:
                deleted["notes"].append("KV namespace was already deleted or not found.")
            else:
                raise

    store = load_store()
    if deploy_id:
        store["deployments"] = [item for item in store.get("deployments", []) if item.get("id") != deploy_id]
    else:
        store["deployments"] = [item for item in store.get("deployments", []) if not (item.get("account_id") == account_id and item.get("worker_name") == worker_name)]
    save_store(store)
    deleted["message"] = "Cleanup completed successfully"
    return deleted


def save_profile(payload: Dict[str, Any]) -> Dict[str, Any]:
    store = load_store()
    profiles = store.setdefault("profiles", [])
    pid = (payload.get("id") or "").strip() or secrets.token_hex(8)
    name = (payload.get("name") or "").strip() or "Cloudflare Profile"
    auth_mode = (payload.get("auth_mode") or "api_token").strip()
    if auth_mode not in ("api_token", "global_key"):
        raise ValueError("Invalid auth type")
    profile = {
        "id": pid,
        "name": name,
        "auth_mode": auth_mode,
        "api_token": (payload.get("api_token") or "").strip(),
        "email": (payload.get("email") or "").strip(),
        "global_key": (payload.get("global_key") or "").strip(),
        "proxy_url": (payload.get("proxy_url") or "").strip(),
        "last_account_id": (payload.get("last_account_id") or payload.get("account_id") or "").strip(),
        "workers_dev_subdomain": safe_name(payload.get("workers_dev_subdomain") or ""),
    }
    # If user edits a profile but leaves secret blank, keep old secret.
    for i, old in enumerate(profiles):
        if old.get("id") == pid:
            if not profile["api_token"]:
                profile["api_token"] = old.get("api_token") or ""
            if not profile["global_key"]:
                profile["global_key"] = old.get("global_key") or ""
            profiles[i] = profile
            save_store(store)
            return public_profile(profile)
    profiles.insert(0, profile)
    save_store(store)
    return public_profile(profile)


def delete_profile(profile_id: str) -> bool:
    store = load_store()
    before = len(store.get("profiles", []))
    store["profiles"] = [p for p in store.get("profiles", []) if p.get("id") != profile_id]
    save_store(store)
    return len(store["profiles"]) != before


class Handler(BaseHTTPRequestHandler):
    server_version = "RKhBPBWizard/1.7"

    def client_has_token(self, parsed=None) -> bool:
        if parsed is not None:
            query_token = (parse_qs(parsed.query).get("token") or [""])[0]
            if secrets.compare_digest(query_token, LOCAL_TOKEN):
                return True
        cookie_header = self.headers.get("Cookie") or ""
        cookies = {}
        for part in cookie_header.split(";"):
            if "=" in part:
                k, v = part.strip().split("=", 1)
                cookies[k] = v
        return secrets.compare_digest(cookies.get(COOKIE_NAME, ""), LOCAL_TOKEN)

    def send_json(self, data: Any, status: int = 200):
        raw = dumps(data)
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def send_html(self, html: str, status: int = 200):
        raw = html.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def send_file(self, path: Path, *, set_token_cookie: bool = False):
        if not path.exists() or not path.is_file():
            self.send_error(404)
            return
        raw = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", mimetypes.guess_type(str(path))[0] or "application/octet-stream")
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        if set_token_cookie:
            self.send_header("Set-Cookie", f"{COOKIE_NAME}={LOCAL_TOKEN}; Path=/; SameSite=Strict")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def unauthorized_page(self):
        return self.send_html("""<!doctype html><meta charset='utf-8'><title>RKh BPB Wizard</title><body style='margin:0;background:#050301;color:#fff;font-family:Segoe UI,Arial;display:grid;place-items:center;min-height:100vh'><div style='max-width:680px;padding:32px;border:1px solid rgba(255,122,0,.35);border-radius:28px;background:rgba(255,122,0,.08);box-shadow:0 30px 80px rgba(0,0,0,.6)'><h1 style='margin:0 0 10px;background:linear-gradient(90deg,#ff7a00,#ffd36a,#ff4d00);-webkit-background-clip:text;color:transparent'>RKh BPB Wizard</h1><p style='line-height:1.7;color:#ffd9b0'>For safety, open the secure local URL printed in CMD. A new token is generated every time the app starts.</p><code style='display:block;padding:14px;border-radius:14px;background:#120700;color:#ffb765'>http://127.0.0.1:8000/?token=...</code></div></body>""", 401)

    def do_GET(self):
        try:
            parsed = urlparse(self.path)
            if parsed.path == "/":
                if parse_qs(parsed.query).get("token"):
                    if self.client_has_token(parsed):
                        return self.send_file(STATIC_DIR / "index.html", set_token_cookie=True)
                    return self.unauthorized_page()
                if self.client_has_token(parsed):
                    return self.send_file(STATIC_DIR / "index.html")
                return self.unauthorized_page()
            if parsed.path.startswith("/api/") and not self.client_has_token(parsed):
                return self.send_json({"ok": False, "error": "Unauthorized. Open the secure local URL printed in CMD."}, 401)
            if parsed.path == "/api/config":
                store = load_store()
                return self.send_json({
                    "ok": True,
                    "profiles": [public_profile(p) for p in store.get("profiles", [])],
                    "deployments": store.get("deployments", []),
                    "suggestion": random_suggestion(),
                })
            if parsed.path == "/api/suggest":
                return self.send_json({"ok": True, "suggestion": random_suggestion()})
            target = (STATIC_DIR / parsed.path.lstrip("/")).resolve()
            if STATIC_DIR.resolve() in target.parents and self.client_has_token(parsed):
                return self.send_file(target)
            self.send_error(404)
        except Exception as exc:
            traceback.print_exc()
            self.send_json({"ok": False, "error": str(exc)}, 500)

    def do_POST(self):
        try:
            parsed = urlparse(self.path)
            if parsed.path.startswith("/api/") and not self.client_has_token(parsed):
                return self.send_json({"ok": False, "error": "Unauthorized. Open the secure local URL printed in CMD."}, 401)
            payload = read_json(self)
            if self.path == "/api/reset_local_data":
                save_store({"profiles": [], "deployments": []})
                return self.send_json({"ok": True, "message": "Local data reset successfully"})
            if self.path == "/api/profiles/save":
                return self.send_json({"ok": True, "profile": save_profile(payload)})
            if self.path == "/api/profiles/delete":
                return self.send_json({"ok": True, "deleted": delete_profile((payload.get("id") or "").strip())})
            if self.path == "/api/accounts":
                merged = merge_profile_payload(payload)
                cf = CFClient(Auth.from_payload(merged), merged.get("proxy_url") or "")
                res = cf.request("GET", "/accounts?per_page=100")
                accounts = [{"id": a.get("id"), "name": a.get("name")} for a in (res.get("result") or [])]
                return self.send_json({"ok": True, "accounts": accounts})
            if self.path == "/api/deploy":
                return self.send_json({"ok": True, "result": deploy(payload), "suggestion": random_suggestion()})
            if self.path == "/api/delete_deploy":
                return self.send_json({"ok": True, "result": cleanup(payload)})
            self.send_json({"ok": False, "error": "Not found"}, 404)
        except Exception as exc:
            traceback.print_exc()
            status = 400
            if isinstance(exc, CFError) and exc.status and 400 <= exc.status < 600:
                status = exc.status
            self.send_json({"ok": False, "error": str(exc)}, status)

    def log_message(self, fmt, *args):
        print("[%s] %s" % (self.log_date_time_string(), fmt % args))


def main():
    if not CONFIG_FILE.exists():
        save_store({"profiles": [], "deployments": []})
    host = os.environ.get("RKH_BPB_HOST", "127.0.0.1")
    port = int(os.environ.get("RKH_BPB_PORT", "8000"))
    httpd = ThreadingHTTPServer((host, port), Handler)
    secure_url = f"http://{host}:{port}/?token={LOCAL_TOKEN}"
    print("🟠 " + APP_NAME)
    print("🌑 Dark Orange Liquid Glass")
    print(f"🔐 Secure local URL: {secure_url}")
    print("🧹 Use Reset Local Data inside the app if your browser autofills old values.")
    print("🛑 Press Ctrl+C to stop.")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
