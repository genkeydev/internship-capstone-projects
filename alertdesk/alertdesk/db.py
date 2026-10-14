"""SQLite persistence. Schema is generic tickets + audit; workflow lives in spec."""

from __future__ import annotations

import os
import sqlite3
from pathlib import Path


def db_path() -> Path:
    env = os.environ.get("ALERTDESK_DB")
    if env:
        return Path(env)
    return Path("data/alertdesk.sqlite")


def connect(path: Path | None = None) -> sqlite3.Connection:
    p = path or db_path()
    p.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(p)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS tickets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            severity TEXT NOT NULL,
            status TEXT NOT NULL,
            assignee TEXT,
            created_by TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS audit_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ticket_id INTEGER,
            actor TEXT NOT NULL,
            action TEXT NOT NULL,
            detail TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            FOREIGN KEY (ticket_id) REFERENCES tickets(id)
        );

        CREATE TRIGGER IF NOT EXISTS audit_events_no_update
        BEFORE UPDATE ON audit_events
        BEGIN
            SELECT RAISE(ABORT, 'audit_events are append-only');
        END;

        CREATE TRIGGER IF NOT EXISTS audit_events_no_delete
        BEFORE DELETE ON audit_events
        BEGIN
            SELECT RAISE(ABORT, 'audit_events are append-only');
        END;
        """
    )
    conn.commit()
