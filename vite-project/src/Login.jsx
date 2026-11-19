import { useState } from 'react';
import { login } from './auth';
import './Login.css';

export default function Login({ onSignup }) {
  const [studentid, setStudentid] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setMessage('');
    try { await login(studentid, password); }
    catch (error) { setMessage(error.message); }
    finally { setBusy(false); }
  }
  return (
    <main className="login-page">
      <form className="login-card" onSubmit={submit}>
        <h1>도서관 로그인</h1>
        <p>학번(관리자는 관리자 ID)과 비밀번호를 입력해 주세요.</p>
        <button type="button" onClick={onSignup}>회원가입</button>
        <label htmlFor="login-studentid">학번 또는 관리자 ID</label>
        {/* 학번은 숫자 9자리, 관리자 ID는 영문으로 시작하는 4~20자 (US-02 AC1) */}
        <input id="login-studentid" name="username" autoComplete="username"
          pattern="[0-9]{9}|[A-Za-z][A-Za-z0-9_]{3,19}" maxLength={20} required
          title="학번 9자리 또는 영문으로 시작하는 관리자 ID"
          value={studentid} onChange={e => setStudentid(e.target.value.trim().slice(0, 20))} />
        <label htmlFor="login-password">비밀번호</label>
        <input id="login-password" type="password" name="password" autoComplete="current-password" required
          value={password} onChange={e => setPassword(e.target.value)} />
        <button type="submit" disabled={busy}>{busy ? '로그인 중…' : '로그인'}</button>
        <p role="alert">{message}</p>
      </form>
    </main>
  );
}
