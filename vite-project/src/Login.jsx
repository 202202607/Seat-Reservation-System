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
        <p>학번과 비밀번호를 입력해 주세요.</p>
        <button type="button" onClick={onSignup}>회원가입</button>
        <label htmlFor="login-studentid">학번</label>
        <input id="login-studentid" name="username" autoComplete="username" inputMode="numeric"
          pattern="[0-9]{9}" maxLength={9} minLength={9} required
          value={studentid} onChange={e => setStudentid(e.target.value.replace(/\D/g, '').slice(0, 9))} />
        <label htmlFor="login-password">비밀번호</label>
        <input id="login-password" type="password" name="password" autoComplete="current-password" required
          value={password} onChange={e => setPassword(e.target.value)} />
        <button type="submit" disabled={busy}>{busy ? '로그인 중…' : '로그인'}</button>
        <p role="alert">{message}</p>
      </form>
    </main>
  );
}
