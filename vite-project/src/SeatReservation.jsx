import { useEffect,  useState } from 'react';
import './SeatReservation.css';
import { apiFetch, getAuth } from './auth';

const MAX_BOOKING_HOURS = 4;
const OPEN_TIME = 9 * 60;
const CLOSE_TIME = 22 * 60;
const FLOORS = [1, 2, 3];

const toMinutes = (time) => {
  const [hour, minute] = time.split(':').map(Number);
  return hour * 60 + minute;
};

const toTime = (minutes) =>
  `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;

const toLocalDate = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

const tomorrow = new Date();
tomorrow.setDate(tomorrow.getDate() + 1);
const DEFAULT_DATE = toLocalDate(tomorrow);
const TODAY = toLocalDate(new Date());

const TIME_OPTIONS = Array.from(
  { length: (CLOSE_TIME - OPEN_TIME) / 30 + 1 },
  (_, index) => toTime(OPEN_TIME + index * 30)
);

async function readJson(response) {
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.detail || body.message || `API 오류 (${response.status})`);
  }
  return response.json();
}

function displayDate(value) {
  if (!value) return '날짜를 선택해 주세요';
  const [year, month, day] = value.split('-');
  return `${year}.${month}.${day}`;
}

export default function SeatReservation({ headerActions }) {
  const [floor, setFloor] = useState(1);
  const [date, setDate] = useState(DEFAULT_DATE);
  const [startTime, setStartTime] = useState('10:00');
  const [endTime, setEndTime] = useState('12:00');
  const [selectedSeatId, setSelectedSeatId] = useState(null);
  const [seatsWithStatus, setSeatsWithStatus] = useState([]);
  const [refreshKey, setRefreshKey] = useState(0);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');

  const duration = toMinutes(endTime) - toMinutes(startTime);
  const timeError =
    duration <= 0
      ? '종료 시간은 시작 시간보다 늦어야 합니다.'
      : duration > MAX_BOOKING_HOURS * 60
        ? `한 번에 최대 ${MAX_BOOKING_HOURS}시간까지 예약할 수 있습니다.`
        : '';

  const isInPast = !date || new Date(`${date}T${startTime}:00`).getTime() <= Date.now();

  const fetchSeats = getAuth() ? apiFetch : fetch;

  useEffect(() => {
    if (!date || timeError) {
        setLoading(false);
        return;
    }
    const controller = new AbortController();
    const params = new URLSearchParams({ floor, date, start: startTime, end: endTime });
    setLoading(true);
    fetchSeats(`/api/seats?${params}`, { signal: controller.signal })
      .then(readJson)
      .then((data) => {
        setSeatsWithStatus(data);
        setSelectedSeatId((prev) => {
            if(prev == null) return null;
            const seat = data.find(s => s.seat_id == prev);
            return seat?.display_status === 'AVAILABLE' ? prev : null;
        });
    })
      .catch((error) => {
        if (error.name !== 'AbortError') {
          setSeatsWithStatus([]);
          setNotice(`좌석 조회 실패: ${error.message}`);
        }
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [floor, date, startTime, endTime, refreshKey]);

  const counts = {
    AVAILABLE: seatsWithStatus.filter((seat) => seat.display_status === 'AVAILABLE').length,
    RESERVED: seatsWithStatus.filter((seat) => seat.display_status === 'RESERVED').length,
    MINE: seatsWithStatus.filter((seat) => seat.display_status === 'MINE').length,
  };

  const selectedSeat = seatsWithStatus.find((seat) => seat.seat_id === selectedSeatId);
  const canReserve = !loading && !saving && !!selectedSeat && !!date && date >= TODAY && !timeError && !isInPast && selectedSeat.display_status === 'AVAILABLE';

const updateFilter = (setter, value, resetSeat = false) => {
  setter(value);

  if (resetSeat) {
    setSelectedSeatId(null);
  }

  setNotice('');
};

  async function reserveSeat() {
    if (!getAuth()) {
      setNotice('로그인 후 예약할 수 있습니다.');
      return;
    }
    if (!canReserve) return;
    setSaving(true);
    try {
      const response = await apiFetch('/api/reservations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          seat_id: selectedSeatId,
          date,
          start_time: startTime,
          end_time: endTime,
        }),
      });
      await readJson(response);
      setSelectedSeatId(null);
      setNotice(`${floor}층 ${selectedSeat.seat_number}번 좌석 예약이 DB에 저장되었습니다.`);
      setRefreshKey((n) => n + 1);
    } catch (error) {
      setNotice(`예약 실패: ${error.message}`);
      setRefreshKey((n) => n + 1);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="library-page">
      <header className="library-header">
        <div className="library-brand">
          <div className="library-brand-mark" aria-hidden="true">▦</div>
          <div>
            <div className="library-brand-title">도서관 좌석 예약</div>
            <div className="library-brand-caption">
              LIBRARY SEAT RESERVATION
            </div>
          </div>
        </div>

        <div className="library-header-actions">
          <span className="library-demo-chip">프론트엔드 데모</span>
          {headerActions}
        </div>
      </header>

      <main className="library-main">
        <div className="library-layout">
          <section className="library-card library-seat-panel" aria-labelledby="seats-title">
            <div className="library-card-heading">
              <div>
                <span className="library-section-label">SEAT MAP</span>
                <h2 id="seats-title">좌석 배치도</h2>
              </div>
              <span className="library-floor-pill">{floor}층 · 제{floor}열람실</span>
            </div>

            <div className="library-floor-tabs" aria-label="층 선택">
              {FLOORS.map((level) => (
                <button
                  key={level}
                  type="button"
                  className={`library-floor-tab ${level === floor ? 'is-current' : ''}`}
                  aria-pressed={level === floor}
                  onClick={() => updateFilter(setFloor, level, true)}
                >
                  {level}층
                </button>
              ))}
            </div>

            <div className="library-legend" aria-label="좌석 상태 범례">
              <span><i className="library-dot is-available" />예약 가능</span>
              <span><i className="library-dot is-reserved" />예약됨</span>
              <span><i className="library-dot is-mine" />내 예약</span>
            </div>

            {loading && <p className="library-small-note">좌석 정보를 조회하는 중...</p>}

            <div className="library-room">
              <div className="library-seat-grid">
                {seatsWithStatus.map((seat) => {
                  const isSelected = selectedSeatId === seat.seat_id;
                  const stateText = seat.display_status === 'MINE' ? '내 예약' : seat.display_status === 'RESERVED' ? '예약됨' : '예약 가능';
                  return (
                    <button
                      type="button"
                      key={seat.seat_id}
                      className={`library-seat is-${seat.display_status.toLowerCase()} ${isSelected ? 'is-selected' : ''}`}
                      aria-label={`${floor}층 ${seat.seat_number}번 좌석, ${stateText}`}
                      aria-pressed={isSelected}
                      disabled={seat.display_status === 'RESERVED'}
                      onClick={() => {
                        if (seat.display_status === 'MINE') {
                          setNotice('내 예약 좌석입니다. 내 예약 목록에서 취소할 수 있습니다.');
                          setSelectedSeatId(null);
                        } else {
                          setSelectedSeatId(isSelected ? null : seat.seat_id);
                          setNotice('');
                        }
                      }}
                    >
                      <span className="library-seat-desk" aria-hidden="true" />
                      <strong>{String(seat.seat_number).padStart(2, '0')}</strong>
                      <small>{isSelected ? '선택됨' : stateText}</small>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="library-seat-stats">
              <span><b>{counts.AVAILABLE}</b> 예약 가능</span>
              <span><b>{counts.RESERVED}</b> 예약됨</span>
              <span><b>{counts.MINE}</b> 내 예약</span>
            </div>
          </section>

          <aside className="library-sidebar">
            <section className="library-card library-booking-panel" aria-labelledby="booking-title">
              <div className="library-card-heading">
                <div className="library-booking-header">
                    <h2 id="booking-title">예약 정보</h2>
                    <div className="library-policy">
                        ⏱ <span>1회 최대 <strong>{MAX_BOOKING_HOURS}시간</strong> 예약</span>
                    </div>
                    </div>
              </div>

              <div className="library-form-field">
                <label htmlFor="library-date">이용 날짜</label>
                <input
                  id="library-date"
                  type="date"
                  min={TODAY}
                  value={date}
                  onChange={(event) => updateFilter(setDate, event.target.value)}
                />
              </div>

              <div className="library-time-fields">
                <div className="library-form-field">
                  <label htmlFor="library-start">시작 시간</label>
                  <select id="library-start" value={startTime} onChange={(event) => updateFilter(setStartTime, event.target.value)}>
                    {TIME_OPTIONS.slice(0, -1).map((time) => <option key={time} value={time}>{time}</option>)}
                  </select>
                </div>
                <div className="library-form-field">
                  <label htmlFor="library-end">종료 시간</label>
                  <select id="library-end" value={endTime} onChange={(event) => updateFilter(setEndTime, event.target.value)}>
                    {TIME_OPTIONS.slice(1).map((time) => <option key={time} value={time}>{time}</option>)}
                  </select>
                </div>
              </div>

              <div className={`library-duration ${timeError ? 'has-error' : ''}`}>
                <span>선택한 이용 시간</span>
                <strong>{duration > 0 ? `${duration / 60}시간` : '확인 필요'}</strong>
              </div>
              {timeError ? <p className="library-field-error" role="alert">{timeError}</p> : null}
              {!timeError && isInPast ? <p className="library-field-error">이미 지난 시간은 예약할 수 없습니다.</p> : null}

              <div className="library-selection-summary">
                <span>선택한 좌석</span>
                <strong>{selectedSeat ? `${floor}층 · ${selectedSeat.seat_number}번 좌석` : '좌석을 선택해 주세요'}</strong>
                <small>{displayDate(date)} · {startTime} ~ {endTime}</small>
              </div>

              <button type="button" className="library-primary-button" onClick={reserveSeat} disabled={!canReserve}>
                {selectedSeat ? '이 좌석 예약하기' : '좌석을 선택해 주세요'}
              </button>
              {notice && <p className="library-notice" role="status">{notice}</p>}
              <p className="library-help">운영 시간 09:00~22:00 · 30분 단위 선택</p>
            </section>
          </aside>
        </div>
      </main>
    </div>
  );
}