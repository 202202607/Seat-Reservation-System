// 같은 탭의 새로고침 시 로그인 유지. 탭을 닫으면 저장된 토큰이 제거됩니다.
const STORAGE_KEY = 'library.auth';
let generation = 0;
let refreshing = null;
const emit = () => window.dispatchEvent(new Event('authchange'));
export function getAuth() {
  try { return JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null'); }
  catch { return null; }
}
function saveAuth(value) {
  if (value) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  else sessionStorage.removeItem(STORAGE_KEY);
  emit();
}
function clearAuth() { generation += 1; saveAuth(null); }

async function authPost(path, body) {
  const response = await fetch(`/api/auth/${path}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'omit', cache: 'no-store', signal: AbortSignal.timeout(15000),
    body: JSON.stringify(body),
  });
  const data = response.status === 204 ? null : await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data?.detail || data?.message || `인증 요청 실패 (${response.status})`);
    error.status = response.status;
    throw error;
  }
  return data;
}
export async function login(studentid, password) {
  const current = generation;
  const data = await authPost('login', { studentid, password });
  if (current !== generation) throw new Error('취소된 로그인입니다.');
  generation += 1;
  saveAuth(data);
  return data;
}

async function refreshTokens() {
  if (refreshing) return refreshing;
  const session = getAuth();
  if (!session?.refreshToken) throw new Error('다시 로그인해 주세요.');
  const current = generation;
  const task = authPost('refresh', { refreshToken: session.refreshToken })
    .then(data => {
      if (current === generation) saveAuth(data);
      return data;
    })
    .catch(error => {
      // 일시적인 네트워크 장애로 로그인 정보를 삭제하지 않습니다.
      if (error.status === 401 && current === generation) clearAuth();
      throw error;
    });
  refreshing = task;
  try { return await task; }
  finally { if (refreshing === task) refreshing = null; }
}

export async function apiFetch(input, options = {}) {
  const url = new URL(input, window.location.origin);
  if (url.origin !== window.location.origin || !url.pathname.startsWith('/api/'))
    throw new Error('apiFetch는 같은 서버의 /api/ 주소에만 사용할 수 있습니다.');
  const session = getAuth();
  if (!session?.accessToken) throw new Error('로그인이 필요합니다.');
  const current = generation;
  const headers = new Headers(options.headers);
  headers.set('Authorization', `Bearer ${session.accessToken}`);
  const request = new Request(url, { ...options, headers, credentials: 'omit' });
  const response = await fetch(request.clone());
  if (response.status !== 401) return response;
  if (request.signal.aborted) throw new DOMException('Aborted', 'AbortError');
  if (current !== generation) throw new Error('로그인 상태가 변경되었습니다.');
  const latest = getAuth();
  // 다른 동시 요청이 이미 갱신한 경우에는 새 Access 토큰을 재사용합니다.
  const next = latest?.accessToken !== session.accessToken ? latest : await refreshTokens();
  if (current !== generation || !next?.accessToken) throw new Error('다시 로그인해 주세요.');
  const retryHeaders = new Headers(request.headers);
  retryHeaders.set('Authorization', `Bearer ${next.accessToken}`);
  const retry = await fetch(new Request(request, { headers: retryHeaders }));
  if (retry.status === 401 && current === generation) clearAuth();
  return retry;
}

export async function logout() {
  let session = getAuth();
  const pending = refreshing;
  clearAuth(); // 서버 응답과 무관하게 즉시 클라이언트 토큰을 삭제합니다.
  // 이미 진행 중인 갱신이 있다면 교체된 Refresh 토큰으로 폐기합니다.
  if (pending) {
    try { session = await pending; } catch { /* 기존 토큰으로 폐기 시도 */ }
  }
  if (!session?.refreshToken) return;
  try { await authPost('logout', { refreshToken: session.refreshToken }); }
  catch (error) {
    if (error.status !== 401) throw new Error('이 기기에서는 로그아웃되었습니다. 서버 연결 실패로 서버 세션 폐기는 확인하지 못했습니다.');
  }
}
