import { useState } from 'react'
import './Signup.css'

// 회원가입 (US-01). 학생은 이메일 인증 후 로그인, 관리자는 관리자 가입 코드로 바로 가입된다.
export default function Signup({ onGoToLogin}) {
  const [isAdmin, setIsAdmin] = useState(false)
  const [name, setName] = useState('')
  const [studentid, setStudentid] = useState('')
  const [adminId, setAdminId] = useState('')
  const [adminCode, setAdminCode] = useState('')
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

    const body = isAdmin
      ? { name, adminId, adminCode, email, password }
      : { name, studentid, email, password }

    try {
      const response = await fetch('/api/auth/signup', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(body)
      });

      const data = await response.json().catch(() => ({}));

      if (response.ok) {
        setSignupCompleted(true);
        setShowResend(!isAdmin);
        setMessage(data.message || (isAdmin ? '관리자 가입이 완료되었습니다.' : '인증 이메일을 확인해 주세요.'));
      } else {
        setShowResend(!isAdmin && response.status === 409);
        setMessage(data.message || `회원가입 요청 실패 (${response.status})`);
      }

    } catch (error) {
      setShowResend(!isAdmin);
      setMessage('서버 연결에 실패했습니다. 가입 여부를 확인해 주세요.');
    }
  }

  async function handleResend() {
    setSending(true);

    try {
      const response = await fetch('/api/auth/email/send', {
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
        <h1>{isAdmin ? '관리자 회원가입' : '회원가입'}</h1>

        <label>
          <input
            type="checkbox"
            checked={isAdmin}
            disabled={signupCompleted}
            onChange={(e) => { setIsAdmin(e.target.checked); setMessage(''); setShowResend(false); }}
          />
          관리자로 가입
        </label>

        <input
          placeholder="이름"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />

        {isAdmin ? (
          <>
            <input
              type="text"
              placeholder="관리자 ID (영문으로 시작 4~20자)"
              value={adminId}
              onChange={(e) => setAdminId(e.target.value.trim().slice(0, 20))}
              pattern="[A-Za-z][A-Za-z0-9_]{3,19}"
              title="영문으로 시작하는 4~20자의 영문·숫자·_"
              required
            />
            <input
              type="password"
              placeholder="관리자 가입 코드"
              value={adminCode}
              onChange={(e) => setAdminCode(e.target.value)}
              autoComplete="off"
              required
            />
          </>
        ) : (
          <input
            type="text"
            placeholder="학번"
            inputMode="numeric"
            value={studentid}
            onChange={(e) => setStudentid(e.target.value.replace(/\D/g, "").slice(0, 9))}
            minLength={9}
            maxLength={9}
            required
          />
        )}

        {/* 학생은 학교 이메일(@o.cnu.ac.kr)로만 가입한다. 서버도 같은 규칙으로 거부한다 (US-01). */}
        <input
          type="email"
          placeholder={isAdmin ? '이메일' : '학교 이메일 (예: 학번@o.cnu.ac.kr)'}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          pattern={isAdmin ? undefined : '.+@[oO]\\.[cC][nN][uU]\\.[aA][cC]\\.[kK][rR]'}
          title={isAdmin ? undefined : '충남대학교 학교 이메일(@o.cnu.ac.kr)만 사용할 수 있습니다.'}
          required
        />

        <input
          type="password"
          placeholder="비밀번호 (8자 이상, 영문·숫자·특수문자 포함)"
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
          <button type="submit">{isAdmin ? '관리자 가입' : '회원가입'}</button>
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
