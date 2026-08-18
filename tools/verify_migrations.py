#!/usr/bin/env python3
"""Host-side audit of the SQL semantics used by BarcodeDatabase migrations."""
from __future__ import annotations

import sqlite3
from contextlib import closing

SCAN_TABLE = """
CREATE TABLE `scanned_items` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `barcode` TEXT NOT NULL,
    `itemCode` TEXT,
    `productName` TEXT,
    `productNameArabic` TEXT,
    `tagType` TEXT NOT NULL,
    `unitType` TEXT NOT NULL,
    `copies` INTEGER NOT NULL,
    `createdAt` INTEGER NOT NULL,
    `updatedAt` INTEGER NOT NULL
)
"""

WIFI_V2 = """
CREATE TABLE IF NOT EXISTS `wifi_print_history` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `posCode` TEXT NOT NULL,
    `englishDesc` TEXT NOT NULL,
    `unitType` TEXT NOT NULL,
    `copies` INTEGER NOT NULL,
    `tagType` TEXT NOT NULL,
    `status` TEXT NOT NULL,
    `reason` TEXT NOT NULL,
    `jobId` INTEGER NOT NULL,
    `timestamp` INTEGER NOT NULL
)
"""

WIFI_V3 = """
CREATE TABLE IF NOT EXISTS `wifi_print_history` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `jobId` INTEGER NOT NULL,
    `timestamp` INTEGER NOT NULL,
    `kind` TEXT NOT NULL,
    `tagType` TEXT NOT NULL,
    `unitType` TEXT NOT NULL,
    `copies` INTEGER NOT NULL,
    `nTags` INTEGER NOT NULL,
    `summary` TEXT NOT NULL,
    `posCode` TEXT NOT NULL,
    `price` TEXT NOT NULL,
    `reason` TEXT NOT NULL,
    `itemsJson` TEXT NOT NULL
)
"""

EXPECTED_SCAN_COLUMNS = [
    ("id", "INTEGER", 1),
    ("barcode", "TEXT", 1),
    ("itemCode", "TEXT", 0),
    ("productName", "TEXT", 0),
    ("productNameArabic", "TEXT", 0),
    ("tagType", "TEXT", 1),
    ("unitType", "TEXT", 1),
    ("copies", "INTEGER", 1),
    ("createdAt", "INTEGER", 1),
    ("updatedAt", "INTEGER", 1),
    ("deletedAt", "INTEGER", 0),
]
EXPECTED_SCAN_INDEXES = {
    "index_scanned_items_barcode_tagType_unitType",
    "index_scanned_items_createdAt",
    "index_scanned_items_deletedAt",
}
EXPECTED_WIFI_COLUMNS = [
    "id", "jobId", "timestamp", "kind", "tagType", "unitType", "copies",
    "nTags", "summary", "posCode", "price", "reason", "itemsJson",
]


def add_scanned_base(db: sqlite3.Connection) -> None:
    db.execute(SCAN_TABLE)
    db.execute(
        "CREATE INDEX index_scanned_items_barcode_tagType_unitType "
        "ON scanned_items (barcode, tagType, unitType)"
    )


def migration_1_2(db: sqlite3.Connection) -> None:
    db.execute(WIFI_V2)
    db.execute("CREATE INDEX IF NOT EXISTS index_wifi_print_history_jobId ON wifi_print_history (jobId)")
    db.execute("CREATE INDEX IF NOT EXISTS index_wifi_print_history_timestamp ON wifi_print_history (timestamp)")


def migration_2_3(db: sqlite3.Connection) -> None:
    db.execute("DROP TABLE IF EXISTS wifi_print_history")
    db.execute(WIFI_V3)
    db.execute("CREATE INDEX IF NOT EXISTS index_wifi_print_history_jobId ON wifi_print_history (jobId)")
    db.execute("CREATE INDEX IF NOT EXISTS index_wifi_print_history_timestamp ON wifi_print_history (timestamp)")


def migration_3_4(db: sqlite3.Connection) -> None:
    db.execute("CREATE INDEX IF NOT EXISTS index_scanned_items_createdAt ON scanned_items (createdAt)")


def migration_4_5(db: sqlite3.Connection) -> None:
    db.execute("ALTER TABLE scanned_items ADD COLUMN deletedAt INTEGER")
    db.execute("CREATE INDEX IF NOT EXISTS index_scanned_items_deletedAt ON scanned_items (deletedAt)")


def make_database_at(version: int) -> sqlite3.Connection:
    db = sqlite3.connect(":memory:")
    add_scanned_base(db)
    if version >= 2:
        migration_1_2(db)
    if version >= 3:
        migration_2_3(db)
    if version >= 4:
        migration_3_4(db)
    if version >= 5:
        migration_4_5(db)
    return db


def verify_version_5(db: sqlite3.Connection, origin: int) -> None:
    found_columns = [(row[1], row[2], row[3]) for row in db.execute("PRAGMA table_info(scanned_items)")]
    assert found_columns == EXPECTED_SCAN_COLUMNS, (origin, found_columns)
    found_indexes = {row[1] for row in db.execute("PRAGMA index_list(scanned_items)")}
    assert found_indexes == EXPECTED_SCAN_INDEXES, (origin, found_indexes)
    wifi_columns = [row[1] for row in db.execute("PRAGMA table_info(wifi_print_history)")]
    assert wifi_columns == EXPECTED_WIFI_COLUMNS, (origin, wifi_columns)
    wifi_indexes = {row[1] for row in db.execute("PRAGMA index_list(wifi_print_history)")}
    assert wifi_indexes == {"index_wifi_print_history_jobId", "index_wifi_print_history_timestamp"}, (origin, wifi_indexes)


def main() -> None:
    migration_map = {1: [migration_1_2, migration_2_3, migration_3_4, migration_4_5], 2: [migration_2_3, migration_3_4, migration_4_5], 3: [migration_3_4, migration_4_5], 4: [migration_4_5]}
    for source_version, migrations in migration_map.items():
        with closing(make_database_at(source_version)) as db:
            db.execute("INSERT INTO scanned_items (barcode, tagType, unitType, copies, createdAt, updatedAt) VALUES ('001', 'A4', 'PCS', 1, 1, 1)")
            for migration in migrations:
                migration(db)
            verify_version_5(db, source_version)
            row = db.execute("SELECT barcode, deletedAt FROM scanned_items").fetchone()
            assert row == ("001", None), (source_version, row)
            print(f"v{source_version}->v5: PASS (data preserved, schema/indexes match)")


if __name__ == "__main__":
    main()
