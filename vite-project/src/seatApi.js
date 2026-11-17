// 좌석 조회(US-04)·예약 생성(US-05) API 호출 모음. 화면 컴포넌트는 이 모듈만 사용한다.
import { apiFetch } from './auth';

export async function readJson(response) {
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.detail || body.message || `API 오류 (${response.status})`);
  }
  return response.json();
}

/** GET /api/seats → { date, start, end, policy, seats: [{ seat_id, seat_number, display_status, ... }] } */
export function fetchSeatStatus({ floor, date, start, end }, signal) {
  const params = new URLSearchParams({ floor, date, start, end });
  return apiFetch(`/api/seats?${params}`, { signal }).then(readJson);
}

/** POST /api/reservations → { message, reservation } */
export function createReservation({ seatId, date, start, end }) {
  return apiFetch('/api/reservations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ seat_id: seatId, date, start_time: start, end_time: end }),
  }).then(readJson);
}

const sleep = (ms, signal) => new Promise((resolve) => {
  const timer = setTimeout(resolve, ms);
  signal.addEventListener('abort', () => { clearTimeout(timer); resolve(); }, { once: true });
});

async function readEvents(body, signal, onEvent) {
  const reader = body.pipeThrough(new TextDecoderStream()).getReader();
  signal.addEventListener('abort', () => reader.cancel().catch(() => {}), { once: true });
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) return;
    buffer += value.replace(/\r\n/g, '\n');
    let boundary;
    while ((boundary = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      let event = 'message';
      const data = [];
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
      }
      if (data.length) onEvent(event, data.join('\n'));
    }
  }
}

/**
 * GET /api/seats/stream 을 구독해 좌석 변경을 실시간으로 받는다.
 * EventSource 는 Authorization 헤더를 붙일 수 없어 fetch 스트림으로 읽고, 끊기면 지수 백오프로 재연결한다.
 * onReconnect 는 재연결 직후(끊긴 사이 놓친 변경을 다시 조회하도록) 호출된다.
 * @returns 구독 해제 함수
 */
export function subscribeSeatChanges(onChange, { onReconnect } = {}) {
  const controller = new AbortController();
  const { signal } = controller;
  let retryMs = 1000;
  let connectedBefore = false;

  (async () => {
    while (!signal.aborted) {
      try {
        const response = await apiFetch('/api/seats/stream', {
          signal, cache: 'no-store', headers: { Accept: 'text/event-stream' },
        });
        if (!response.ok || !response.body) throw new Error(`좌석 스트림 연결 실패 (${response.status})`);
        retryMs = 1000;
        if (connectedBefore) onReconnect?.();
        connectedBefore = true;
        await readEvents(response.body, signal, (event, data) => {
          if (event !== 'seat-changed') return;
          try { onChange(JSON.parse(data)); } catch { /* 잘못된 이벤트는 무시 */ }
        });
      } catch {
        // 네트워크 오류·서버 재시작 등. 아래에서 잠시 후 다시 연결한다.
      }
      if (signal.aborted) return;
      await sleep(retryMs, signal);
      retryMs = Math.min(retryMs * 2, 30000);
    }
  })();

  return () => controller.abort();
}
