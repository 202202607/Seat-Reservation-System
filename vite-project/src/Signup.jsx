import { useState } from 'react'
import './Signup.css'


export default function Signup({ onGoToLogin}) {
  const [name, setName] = useState('')
  const [studentid, setStudentid] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [message, setMessage] = useState('')

  const [signupCompleted, setSignupCompleted] = useState(false);
  const [sending, setSending] = useState(false);

  const [showResend, setShowResend] = useState(false);

  const handleSignup = async (e) => {
    e.preventDefault()

    if (password !== confirmPassword) {
      setMessage('비밀번호가 일치하지 않습니다.')
      return
    }

    try {
      const response = await fetch('/api/signup', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ name, studentid, email, password })
      });

      const data = await response.json().catch(() => ({}));

      if (response.ok) {
        setSignupCompleted(true);
        setShowResend(true);
        setMessage(data.message || '인증 이메일을 확인해 주세요.');
      } else {
        setShowResend(true);
        setMessage(data.message || `회원가입 요청 실패 (${response.status})`);
      }

    } catch (error) {
      setShowResend(true);
      setMessage('서버 연결에 실패했습니다. 가입 여부를 확인해 주세요.');
    }
  }

  async function handleResend() {
    setSending(true);

    try {
      const response = await fetch('/api/auth/resend-verification', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });

      if (!response.ok) {
        throw new Error('인증 메일 재발송에 실패했습니다.');
      }

      alert('인증 메일을 다시 발송했습니다.');
    } catch (error) {
      alert(error.message);
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="signup">
      <form onSubmit={handleSignup}>
        <h1>회원가입</h1>

        <input
          placeholder="이름"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        <input
          type="text"
          placeholder="학번"
          value={studentid}
          onChange={(e) => setStudentid(e.target.value.replace(/\D/g, "").slice(0, 9))}
          minLength={9}
          maxLength={9}
          required
        />

        <input
          type="email"
          placeholder="이메일"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />

        <input
          type="password"
          placeholder="비밀번호 (8자 이상)"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          minLength={8}
          required
        />

        <input
          type="password"
          placeholder="비밀번호 확인"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          required
        />

        {!signupCompleted && (
          <button type="submit">회원가입</button>
        )}

        {showResend && (
          <button type="button" onClick={handleResend} disabled={sending}>
            {sending ? '발송 중...' : '인증 메일 재발송'}
          </button>
        )}

        <button type="button" onClick={onGoToLogin}>
          로그인 화면으로 돌아가기
        </button>
        <p>{message}</p>
      </form>
    </div>
  )
}