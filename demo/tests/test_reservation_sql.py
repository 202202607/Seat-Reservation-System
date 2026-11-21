"""Run with python3 -m unittest discover -s demo/tests; tests SQL text against SQLite.

This checks the predicates and data effects, not Oracle/JDBC or the HTTP stack.
"""

import datetime
import pathlib
import re
import sqlite3
import unittest


SOURCE = pathlib.Path(__file__).resolve().parents[1] / "src/main/java/com/example/demo/reservation/ReservationRepository.java"
JAVA = SOURCE.read_text(encoding="utf-8")


def sql_in(method):
    body = JAVA.split("public " + method, 1)[1]
    return re.search(r'"""(.*?)"""', body, re.S).group(1)


class ReservationSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("CREATE TABLE seats (seat_id INTEGER, floor INTEGER, seat_number INTEGER, room_name TEXT)")
        self.db.execute("CREATE TABLE reservations (reservation_id INTEGER PRIMARY KEY, seat_id INTEGER, user_id INTEGER, start_time TEXT, end_time TEXT, status TEXT, cancelled_at TEXT, cancelled_by TEXT)")
        self.db.execute("CREATE TABLE app_members (id INTEGER, status TEXT, role TEXT, no_show_count INTEGER)")
        self.db.executemany("INSERT INTO app_members VALUES (?, ?, ?, 0)", [
            (7, "ACTIVE", "ROLE_STUDENT"), (8, "PENDING", "ROLE_STUDENT"),
            (9, "ACTIVE", "ROLE_ADMIN"),
        ])
        self.db.execute("INSERT INTO seats VALUES (5, 2, 10, 'A')")
        self.db.executemany("INSERT INTO reservations VALUES (?, 5, ?, ?, ?, ?, NULL, NULL)", [
            (1, 7, "2026-11-18 11:00:00", "2026-11-18 12:00:00", "RESERVED"),
            (2, 8, "2026-11-18 13:00:00", "2026-11-18 14:00:00", "RESERVED"),
            (3, 7, "2026-11-17 11:00:00", "2026-11-17 12:00:00", "CHECKED_IN"),
            (4, 7, "2026-11-16 11:00:00", "2026-11-16 12:00:00", "CANCELLED"),
        ])
        self.now = "2026-11-18 10:00:00"
        self.cancel_sql = sql_in("int cancelIfAllowed")

    def cancel(self, reservation_id, user=7, at=None):
        # 서비스는 startedAfter = 지금 - 체크인 제한 시간(15분)을 넘긴다 (US-09 '체크인 대기' 취소 허용).
        at = at or self.now
        started_after = (datetime.datetime.fromisoformat(at) - datetime.timedelta(minutes=15)).strftime("%Y-%m-%d %H:%M:%S")
        return self.db.execute(self.cancel_sql, (at, reservation_id, user, started_after)).rowcount

    def test_history_is_owned_sorted_and_includes_past_cancelled(self):
        rows = self.db.execute(sql_in("List<ReservationHistoryItem> findHistory"), (7,)).fetchall()
        self.assertEqual([row[0] for row in rows], [1, 3, 4])
        self.assertEqual(self.db.execute(sql_in("List<ReservationHistoryItem> findHistory"), (99,)).fetchall(), [])

    def test_only_active_student_is_eligible(self):
        sql = sql_in("boolean isActiveStudent")
        self.assertEqual(self.db.execute(sql, (7,)).fetchone()[0], 1)
        self.assertEqual(self.db.execute(sql, (8,)).fetchone()[0], 0)
        self.assertEqual(self.db.execute(sql, (9,)).fetchone()[0], 0)

    def test_owner_state_repeat_and_strict_time_boundary(self):
        self.assertEqual(self.cancel(2), 0)
        self.assertEqual(self.cancel(3), 0)
        self.assertEqual(self.cancel(4), 0)
        # 11:00 시작 예약: 11:15(시작 + 15분)부터는 취소 불가, 11:14:59(체크인 대기 중)는 취소 가능
        self.assertEqual(self.cancel(1, at="2026-11-18 11:15:00"), 0)
        self.assertEqual(self.cancel(1, at="2026-11-18 11:15:01"), 0)
        self.assertEqual(self.cancel(1, at="2026-11-18 11:14:59"), 1)
        self.assertEqual(self.cancel(1), 0)
        status, at, by = self.db.execute("SELECT status, cancelled_at, cancelled_by FROM reservations WHERE reservation_id=1").fetchone()
        self.assertEqual((status, at, by), ("CANCELLED", "2026-11-18 11:14:59", "STUDENT"))
        self.assertEqual(self.db.execute("SELECT no_show_count FROM app_members WHERE id=7").fetchone()[0], 0)
        self.assertEqual(self.db.execute("SELECT COUNT(*) FROM reservations WHERE seat_id=5 AND status IN ('RESERVED','CHECKED_IN') AND start_time < ? AND end_time > ?", ("2026-11-18 12:00:00", "2026-11-18 11:00:00")).fetchone()[0], 0)


if __name__ == "__main__":
    unittest.main()
