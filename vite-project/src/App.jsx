import { useEffect, useRef, useState } from 'react';
import Login from './Login';
import Signup from './Signup';
import SeatReservation from './SeatReservation';
import ReservationHistory from './ReservationHistory';
import { getAuth, logout } from './auth';

export default function App() {
  const [session, setSession] = useState(getAuth);
  const [authPage, setAuthPage] = useState(null);
  const [notice, setNotice] = useState('');
  const [loggingOut, setLoggingOut] = useState(false);
  const [page, setPage] = useState('seats');

  const userIdRef = useRef(session?.user?.id);

  useEffect(() => {
    const update = () => {
      const next = getAuth();
      // 토큰 갱신으로는 이동하지 않고, 로그인 회원이 바뀔 때만 초기화합니다.
      if (userIdRef.current !== next?.user?.id) setPage('seats');
      userIdRef.current = next?.user?.id;
      setSession(next);
      setAuthPage(null);
    };

    window.addEventListener('authchange', update);
    return () => window.removeEventListener('authchange', update);
  }, []);

  async function handleLogout() {
    setNotice('');
    setLoggingOut(true);

    try {
      await logout();
    } catch (error) {
      setNotice(error.message);
    } finally {
      setLoggingOut(false);
    }
  }

  const headerActions = (
    <nav className="auth-nav" aria-label="회원 메뉴">
      {session ? (
        <>
          <span>{session.user.name} ({session.user.studentid})</span>
          <button type="button" onClick={() => setPage(value => value === 'history' ? 'seats' : 'history')}>
            {page === 'history' ? '좌석 예약' : '내 예약 이력'}
          </button>
          <button type="button" disabled={loggingOut} onClick={handleLogout}>로그아웃</button>
        </>
      ) : (
        <button type="button" onClick={() => setAuthPage(value => value === 'login' ? null : 'login')}>로그인</button>
      )}
    </nav>
  );

  return (
    <>
      {notice && <p role="status">{notice}</p>}
      {loggingOut && <p role="status">로그아웃 처리 중…</p>}

      {!session && authPage === 'login' && (
        <Login onSignup={() => setAuthPage('signup')} />
      )}
      {!session && authPage === 'signup' && (
        <Signup onGoToLogin={() => setAuthPage('login')} />
      )}

      {session && page === 'history' ? (
        <ReservationHistory key={session.user.id} headerActions={headerActions} />
      ) : (
        <SeatReservation
          key={session?.user?.id ?? 'guest'}
          headerActions={headerActions}
        />
      )}
    </>
  );
}
