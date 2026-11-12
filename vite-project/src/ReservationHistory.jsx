import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from './auth';
import './ReservationHistory.css';

const STATUS_LABELS = {
  RESERVED: '예약 예정', IN_USE: '이용 중', COMPLETED: '이용 완료', CANCELLED: '취소',
};
const STATUS_CLASS = {
  RESERVED: 'reserved', IN_USE: 'in-use', COMPLETED: 'completed', CANCELLED: 'cancelled',
};

async function readJson(response) {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.detail || body.message || `API 오류 (${response.status})`);
  return body;
}

export default function ReservationHistory({ headerActions }) {
  const [items, setItems] = useState([]);
  const [status, setStatus] = useState('ALL');
  const [sort, setSort] = useState('latest');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [actionError, setActionError] = useState('');
  const [cancellingId, setCancellingId] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [now, setNow] = useState(Date.now);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError('');
    apiFetch('/api/reservations/history', { signal: controller.signal })
      .then(readJson)
      .then(data => {
        if (!Array.isArray(data)) throw new Error('예약 이력 응답 형식이 올바르지 않습니다.');
        if (!controller.signal.aborted) { setItems(data); setNow(Date.now()); }
      })
      .catch(e => { if (!controller.signal.aborted) setError(e.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [refreshKey]);

  // 화면을 열어 둔 동안에도 시작/종료 경계에 따라 상태와 취소 버튼을 갱신합니다.
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const reservations = useMemo(() => items.map(item => {
    if (!['RESERVED', 'IN_USE'].includes(item.status)) return item;
    const start = Date.parse(item.starts_at), end = Date.parse(item.ends_at);
    const displayStatus = end <= now ? 'COMPLETED' : start <= now ? 'IN_USE' : 'RESERVED';
    return { ...item, status: displayStatus, can_cancel: item.can_cancel && start > now };
  }), [items, now]);

  const filtered = useMemo(() => reservations
    .filter(item => status === 'ALL' || item.status === status)
    .sort((a, b) => {
      const order = Date.parse(b.starts_at) - Date.parse(a.starts_at) || b.id - a.id;
      return sort === 'latest' ? order : -order;
    }), [reservations, status, sort]);

  async function cancelReservation(item) {
    if (cancellingId !== null || !item.can_cancel) return;
    if (!window.confirm(`${item.date} ${item.start_time} 예약을 취소할까요?`)) return;
    setCancellingId(item.id); setActionError(''); setNotice('');
    try {
      const result = await readJson(await apiFetch(`/api/reservations/history/${item.id}`, { method: 'DELETE' }));
      setItems(current => current.map(value => value.id === item.id ? { ...value, status: 'CANCELLED', can_cancel: false } : value));
      setNotice(result.message || '예약이 취소되었습니다.');
    } catch (e) { setActionError(e.message); }
    finally { setCancellingId(null); setRefreshKey(value => value + 1); }
  }

  return (
    <div className="rh-page">
      <header className="rh-header">
        <div><p className="rh-eyebrow">도서관 좌석 예약</p><h1>내 예약 이력</h1></div>
        {headerActions}
      </header>
      <main className="rh-main">
        <section className="rh-panel" aria-labelledby="history-title">
          <div className="rh-heading"><div><h2 id="history-title">전체 예약 내역</h2>
            <p>지난 예약과 취소 내역까지 한곳에서 확인하세요.</p></div>
            <button className="rh-refresh" type="button" disabled={loading || cancellingId !== null}
              onClick={() => setRefreshKey(value => value + 1)}>새로고침</button>
          </div>
          <div className="rh-summary">
            <div><span>전체</span><strong>{reservations.length}</strong></div>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <div key={value}><span>{label}</span><strong>{reservations.filter(item => item.status === value).length}</strong></div>
            ))}
          </div>
          <div className="rh-controls">
            <label>예약 상태<select value={status} onChange={e => setStatus(e.target.value)}>
              <option value="ALL">전체 상태</option>
              {Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select></label>
            <label>정렬<select value={sort} onChange={e => setSort(e.target.value)}>
              <option value="latest">이용일 최신순</option><option value="oldest">이용일 오래된순</option>
            </select></label>
            <span className="rh-count">{filtered.length}건</span>
          </div>
          {notice && <p className="rh-success" role="status">{notice}</p>}
          {actionError && <p className="rh-error" role="alert">{actionError}</p>}
          {error && <div className="rh-error" role="alert">{error}
            <button type="button" disabled={loading || cancellingId !== null} onClick={() => setRefreshKey(value => value + 1)}>다시 조회</button>
          </div>}
          {loading ? <p className="rh-empty" role="status">예약 이력을 불러오는 중…</p> : error ? null : filtered.length === 0 ? (
            <p className="rh-empty">{items.length === 0 ? '아직 예약 이력이 없습니다.' : '선택한 상태의 예약이 없습니다.'}</p>
          ) : <div className="rh-list" aria-label="예약 목록">
            {filtered.map(item => <article key={item.id} className="rh-reservation">
              <div className="rh-card-top"><span className={`rh-badge rh-${STATUS_CLASS[item.status] || 'unknown'}`}>
                {STATUS_LABELS[item.status] || item.status || '알 수 없음'}
              </span><span className="rh-id">예약 #{item.id}</span></div>
              <div className="rh-card-body">
                <div><h3>{item.floor == null ? '좌석 정보 없음' : `${item.floor}층 ${item.seat_number}번 좌석`}</h3>
                  {item.room_name && <p className="rh-room">{item.room_name}</p>}
                  <p className="rh-date">{item.date.replaceAll('-', '.')}<span>{item.start_time}–{item.end_time}</span></p>
                </div>
                <button type="button" className="rh-cancel" disabled={!item.can_cancel || cancellingId !== null}
                  onClick={() => cancelReservation(item)} aria-label={`예약 ${item.id} 취소`}>
                  {cancellingId === item.id ? '취소 중…' : item.status === 'CANCELLED' ? '취소됨' : item.can_cancel ? '예약 취소' : '취소 불가'}
                </button>
              </div>
            </article>)}
          </div>}
          <p className="rh-policy">예약 취소는 이용 시작 전까지 가능합니다. 정렬은 예약한 이용 날짜와 시간을 기준으로 합니다.</p>
        </section>
      </main>
    </div>
  );
}
