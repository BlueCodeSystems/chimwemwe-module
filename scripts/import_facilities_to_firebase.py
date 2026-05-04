#!/usr/bin/env python3
"""
Import facilities from an Excel (.xlsx) file into Firebase Realtime Database (RTDB).

This is intended for one-time/admin data loading, not for on-device runtime sync.

Prereqs (run locally, not in the app):
  pip install firebase-admin openpyxl

You must provide a Firebase Admin SDK service account JSON file.
Download from Firebase Console → Project settings → Service accounts → "Generate new private key".

Example:
  python scripts/import_facilities_to_firebase.py ^
    --xlsx facilities.xlsx ^
    --database-url https://chimwemwe-app-default-rtdb.firebaseio.com ^
    --service-account-json serviceAccountKey.json ^
    --node facilities

Excel columns (header row must include these, case-insensitive):
  - facility_name (or Facility / Facility Name / Health Facility)
  - district
  - province
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple


def _slug(s: str) -> str:
    s = (s or "").strip().lower()
    s = re.sub(r"[^a-z0-9]+", "-", s)
    s = re.sub(r"-{2,}", "-", s).strip("-")
    return s or "unknown"


def _norm(s: Optional[str]) -> str:
    return (s or "").strip()


@dataclass(frozen=True)
class FacilityRow:
    facility_name: str
    district: str
    province: str

    def key(self) -> str:
        # Deterministic key so re-import is idempotent.
        return _slug(f"{self.facility_name}-{self.district}-{self.province}")

    def to_dict(self) -> Dict[str, Any]:
        return {
            "facility_name": self.facility_name,
            "district": self.district,
            "province": self.province,
        }


def _detect_headers(header_cells: Iterable[Any]) -> Dict[str, int]:
    """
    Map required fields to column indexes using flexible header matching.
    """
    headers = []
    for cell in header_cells:
        v = ""
        try:
            v = str(cell.value) if cell is not None else ""
        except Exception:
            v = ""
        headers.append(_slug(v))

    def find(*candidates: str) -> int:
        for cand in candidates:
            cand_slug = _slug(cand)
            if cand_slug in headers:
                return headers.index(cand_slug)
        return -1

    mapping = {
        "facility_name": find("facility_name", "facility", "facility name", "health facility", "health_facility"),
        "district": find("district", "district_name"),
        "province": find("province", "province_name", "region"),
    }
    missing = [k for k, idx in mapping.items() if idx < 0]
    if missing:
        raise ValueError(
            "Missing required columns in header row: "
            + ", ".join(missing)
            + ". Found headers: "
            + ", ".join(headers)
        )
    return mapping


def _read_xlsx(path: Path, sheet: Optional[str]) -> List[FacilityRow]:
    try:
        import openpyxl  # type: ignore
    except Exception as e:
        raise RuntimeError("openpyxl is required. Install with: pip install openpyxl") from e

    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb[sheet] if sheet else wb.active

    rows = ws.iter_rows(values_only=False)
    try:
        header = next(rows)
    except StopIteration:
        return []

    header_map = _detect_headers(header)

    out: List[FacilityRow] = []
    for r in rows:
        def get(col_key: str) -> str:
            idx = header_map[col_key]
            cell = r[idx] if idx < len(r) else None
            try:
                return _norm(str(cell.value) if cell and cell.value is not None else "")
            except Exception:
                return ""

        facility_name = get("facility_name")
        district = get("district")
        province = get("province")

        if not facility_name and not district and not province:
            continue  # skip empty row
        if not facility_name:
            raise ValueError("Row missing facility_name (blank).")

        out.append(FacilityRow(facility_name=facility_name, district=district, province=province))
    return out


def _init_firebase(service_account_json: Path, database_url: str):
    try:
        import firebase_admin  # type: ignore
        from firebase_admin import credentials, db  # type: ignore
    except Exception as e:
        raise RuntimeError("firebase-admin is required. Install with: pip install firebase-admin") from e

    if not firebase_admin._apps:
        cred = credentials.Certificate(str(service_account_json))
        firebase_admin.initialize_app(cred, {"databaseURL": database_url})
    return db


def _upload(rows: List[FacilityRow], db_mod, node: str, dry_run: bool) -> Tuple[int, int]:
    ref = db_mod.reference(node)
    created = 0
    updated = 0

    for row in rows:
        key = row.key()
        payload = row.to_dict()
        if dry_run:
            print(f"[DRY RUN] {node}/{key} => {payload}")
            continue

        existing = ref.child(key).get()
        if existing is None:
            created += 1
        else:
            updated += 1
        ref.child(key).set(payload)

    return created, updated


def main(argv: List[str]) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--xlsx", required=True, help="Path to .xlsx file")
    ap.add_argument("--sheet", default=None, help="Optional sheet name; defaults to active sheet")
    ap.add_argument("--database-url", required=True, help="RTDB URL, e.g. https://chimwemwe-app-default-rtdb.firebaseio.com")
    ap.add_argument("--service-account-json", required=True, help="Path to Firebase Admin SDK service account JSON")
    ap.add_argument("--node", default="facilities", help="RTDB node to write to (default: facilities)")
    ap.add_argument("--dry-run", action="store_true", help="Print what would be written without writing")
    args = ap.parse_args(argv)

    xlsx_path = Path(args.xlsx).expanduser().resolve()
    sa_path = Path(args.service_account_json).expanduser().resolve()

    if not xlsx_path.exists():
        print(f"ERROR: xlsx not found: {xlsx_path}", file=sys.stderr)
        return 2
    if not sa_path.exists():
        print(f"ERROR: service account JSON not found: {sa_path}", file=sys.stderr)
        return 2

    rows = _read_xlsx(xlsx_path, args.sheet)
    if not rows:
        print("No rows found; nothing to upload.")
        return 0

    db_mod = _init_firebase(sa_path, args.database_url)
    created, updated = _upload(rows, db_mod, args.node, args.dry_run)

    if args.dry_run:
        print(f"Dry run complete. Rows: {len(rows)}")
    else:
        print(f"Upload complete. Rows: {len(rows)} (created={created}, updated={updated})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
