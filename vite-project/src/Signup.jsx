import { useState } from 'react'
import './Signup.css'


export default function Signup({ onGoToLogin}) {
  const [name, setName] = useState('')
  const [studentid, setStudentid] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [message, setMessage] = useState('')

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
      })

      const data = await response.json()
      setMessage(data.message || '처리 결과를 확인할 수 없습니다.')
    } catch (error) {
      setMessage('서버 연결에 실패했습니다.')
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

        <button type="submit">회원가입</button>
        <button type="button" onClick={onGoToLogin}>
          로그인 화면으로 돌아가기
        </button>
        <p>{message}</p>
      </form>
    </div>
  )
}