import { useEffect, useRef, useState } from 'react';
import './SeatReservation.css';
import { getAuth } from './auth';
import { createReservation, fetchSeatStatus, subscribeSeatChanges } from './seatApi';

const FLOORS = [1, 2, 3];

// 좌석 표시 상태 (US-04 AC2). 키는 서버의 display_status 값.
const SEAT_STATES = {
  AVAILABLE: { label: '예약 가능', className: 'is-available' },
  IN_USE: { label: '사용 중', className: 'is-in-use' },
  MINE: { label: '내 예약', className: 'is-mine' },
  RESERVED: { label: '예약됨', className: 'is-reserved' },
};

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

// 운영 시간·예약 단위는 서버 정책(policy)에서 받아 선택지를 만든다.
function buildTimeOptions(policy) {
  if (!policy) return null;
  const open = toMinutes(policy.open_time);
  const close = toMinutes(policy.close_time);
  const step = policy.slot_minutes;
  return Array.from({ length: Math.floor((close - open) / step) + 1 }, (_, index) => toTime(open + index * step));
}

function displayDate(value) {
  if (!value) return '날짜를 선택해 주세요';
  const [year, month, day] = value.split('-');
  return `${year}.${month}.${day}`;
}

export default function SeatReservation({ headerActions }) {
  const loggedIn = !!getAuth();
  const [floor, setFloor] = useState(1);
  const [date, setDate] = useState(DEFAULT_DATE);
  const [startTime, setStartTime] = useState('10:00');
  const [endTime, setEndTime] = useState('12:00');
  const [selectedSeatId, setSelectedSeatId] = useState(null);
  const [seatsWithStatus, setSeatsWithStatus] = useState([]);
  const [policy, setPolicy] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');

  const duration = toMinutes(endTime) - toMinutes(startTime);
  const maxHours = policy?.max_reservation_hours;
  const timeError =
    duration <= 0
      ? '종료 시간은 시작 시간보다 늦어야 합니다.'
      : maxHours && duration > maxHours * 60
        ? `한 번에 최대 ${maxHours}시간까지 예약할 수 있습니다.`
        : '';
  const rangeError =
    policy && date && (date < policy.bookable_from || date > policy.bookable_until)
      ? `예약은 ${displayDate(policy.bookable_from)} ~ ${displayDate(policy.bookable_until)}(오늘~+${policy.max_advance_days}일)만 가능합니다.`
      : '';
  const isInPast = !date || new Date(`${date}T${startTime}:00`).getTime() <= Date.now();

  // 조회 조건과 무관한 실시간 구독에서 최신 조건을 읽기 위한 ref
  const viewRef = useRef({ floor, date });
  viewRef.current = { floor, date };

  useEffect(() => {
    if (!loggedIn || !date || duration <= 0) {
      setLoading(false);
      return undefined;
    }
    const controller = new AbortController();
    setLoading(true);
    fetchSeatStatus({ floor, date, start: startTime, end: endTime }, controller.signal)
      .then((data) => {
        setPolicy(data.policy);
        setSeatsWithStatus(data.seats);
        setSelectedSeatId((prev) => {
          if (prev == null) return null;
          const seat = data.seats.find((s) => s.seat_id === prev);
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
  }, [loggedIn, floor, date, startTime, endTime, duration, refreshKey]);

  // 다른 학생의 예약 등으로 좌석 상태가 바뀌면 보고 있는 층·날짜일 때만 다시 조회한다.
  useEffect(() => {
    if (!loggedIn) return undefined;
    const refresh = () => setRefreshKey((n) => n + 1);
    return subscribeSeatChanges((change) => {
      const view = viewRef.current;
      if (change.floor === view.floor && change.date === view.date) refresh();
    }, { onReconnect: refresh });
  }, [loggedIn]);

  const counts = Object.fromEntries(
    Object.keys(SEAT_STATES).map((state) => [state, seatsWithStatus.filter((seat) => seat.display_status === state).length])
  );

  const timeOptions = buildTimeOptions(policy);
  const startOptions = timeOptions ? timeOptions.slice(0, -1) : [startTime];
  const endOptions = timeOptions ? timeOptions.slice(1) : [endTime];

  const selectedSeat = seatsWithStatus.find((seat) => seat.seat_id === selectedSeatId);
  const canReserve = loggedIn && !loading && !saving && !!selectedSeat && !!date && !!policy
    && !timeError && !rangeError && !isInPast && selectedSeat.display_status === 'AVAILABLE';

  const updateFilter = (setter, value, resetSeat = false) => {
    setter(value);
    if (resetSeat) setSelectedSeatId(null);
    setNotice('');
  };

  async function reserveSeat() {
    if (!canReserve) return;
    setSaving(true);
    try {
      await createReservation({ seatId: selectedSeatId, date, start: startTime, end: endTime });
      setSelectedSeatId(null);
      setNotice(`${floor}층 ${selectedSeat.seat_number}번 좌석 예약이 완료되었습니다.`);
    } catch (error) {
      setNotice(`예약 실패: ${error.message}`);
    } finally {
      setSaving(false);
      setRefreshKey((n) => n + 1);
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
              {Object.entries(SEAT_STATES).map(([state, { label, className }]) => (
                <span key={state}><i className={`library-dot ${className}`} />{label}</span>
              ))}
            </div>

            {!loggedIn && (
              <p className="library-small-note" role="status">로그인한 학생만 좌석 현황을 조회할 수 있습니다. 먼저 로그인해 주세요.</p>
            )}
            {loading && <p className="library-small-note">좌석 정보를 조회하는 중...</p>}

            <div className="library-room">
              <div className="library-seat-grid">
                {seatsWithStatus.map((seat) => {
                  const isSelected = selectedSeatId === seat.seat_id;
                  const state = SEAT_STATES[seat.display_status] ?? SEAT_STATES.RESERVED;
                  const occupiedByOther = seat.display_status === 'RESERVED' || seat.display_status === 'IN_USE';
                  return (
                    <button
                      type="button"
                      key={seat.seat_id}
                      className={`library-seat ${state.className} ${isSelected ? 'is-selected' : ''}`}
                      aria-label={`${floor}층 ${seat.seat_number}번 좌석, ${state.label}`}
                      aria-pressed={isSelected}
                      disabled={occupiedByOther}
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
                      <small>{isSelected ? '선택됨' : state.label}</small>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="library-seat-stats">
              {Object.entries(SEAT_STATES).map(([state, { label }]) => (
                <span key={state}><b>{counts[state]}</b> {label}</span>
              ))}
            </div>
          </section>

          <aside className="library-sidebar">
            <section className="library-card library-booking-panel" aria-labelledby="booking-title">
              <div className="library-card-heading">
                <div className="library-booking-header">
                  <h2 id="booking-title">예약 정보</h2>
                  {policy && (
                    <div className="library-policy">
                      ⏱ <span>1회 최대 <strong>{policy.max_reservation_hours}시간</strong> 예약</span>
                    </div>
                  )}
                </div>
              </div>

              <div className="library-form-field">
                <label htmlFor="library-date">이용 날짜</label>
                <input
                  id="library-date"
                  type="date"
                  min={policy?.bookable_from ?? TODAY}
                  max={policy?.bookable_until}
                  value={date}
                  onChange={(event) => updateFilter(setDate, event.target.value)}
                />
              </div>

              <div className="library-time-fields">
                <div className="library-form-field">
                  <label htmlFor="library-start">시작 시간</label>
                  <select id="library-start" value={startTime} onChange={(event) => updateFilter(setStartTime, event.target.value)}>
                    {startOptions.map((time) => <option key={time} value={time}>{time}</option>)}
                  </select>
                </div>
                <div className="library-form-field">
                  <label htmlFor="library-end">종료 시간</label>
                  <select id="library-end" value={endTime} onChange={(event) => updateFilter(setEndTime, event.target.value)}>
                    {endOptions.map((time) => <option key={time} value={time}>{time}</option>)}
                  </select>
                </div>
              </div>

              <div className={`library-duration ${timeError ? 'has-error' : ''}`}>
                <span>선택한 이용 시간</span>
                <strong>{duration > 0 ? `${duration / 60}시간` : '확인 필요'}</strong>
              </div>
              {timeError ? <p className="library-field-error" role="alert">{timeError}</p> : null}
              {!timeError && rangeError ? <p className="library-field-error" role="alert">{rangeError}</p> : null}
              {!timeError && !rangeError && isInPast ? <p className="library-field-error">이미 지난 시간은 예약할 수 없습니다.</p> : null}

              <div className="library-selection-summary">
                <span>선택한 좌석</span>
                <strong>{selectedSeat ? `${floor}층 · ${selectedSeat.seat_number}번 좌석` : '좌석을 선택해 주세요'}</strong>
                <small>{displayDate(date)} · {startTime} ~ {endTime}</small>
              </div>

              <button type="button" className="library-primary-button" onClick={reserveSeat} disabled={!canReserve}>
                {!loggedIn ? '로그인 후 예약할 수 있습니다' : selectedSeat ? '이 좌석 예약하기' : '좌석을 선택해 주세요'}
              </button>
              {notice && <p className="library-notice" role="status">{notice}</p>}
              {policy && (
                <p className="library-help">
                  운영 시간 {policy.open_time}~{policy.close_time} · {policy.slot_minutes}분 단위 · 오늘부터 {policy.max_advance_days}일 후까지 예약 가능
                </p>
              )}
            </section>
          </aside>
        </div>
      </main>
    </div>
  );
}
