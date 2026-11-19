# US-06 / US-09 시연 메모

## 기준과 확인 사항

- 기준 문서: `시스템구조설계및개발환경.pdf`, `스프린트-1 데모 준비 및 평가 루브릭 안내.pdf`.
- 저장소에는 Product Backlog 원문과 AC 목록, DB DDL, Maven/Gradle/Vite 설정 파일이 없다. 아래 조건은 설계서와 기존 화면 동작을 근거로 한 검증 조건이며 Backlog 원문 AC라고 주장하지 않는다.
- 내역 기본순서는 **이용 시작시각 최신순**, 같은 시각에는 예약 ID 내림차순이다. 기존 화면의 `이용일 최신순`과 일치한다.
- 저장 상태는 `RESERVED`, `CHECKED_IN`, `CANCELLED`, `AUTO_CANCELLED`를 그대로 반환한다. 화면의 `이용 시간 중`·`지난 예약`은 `RESERVED`와 현재 시각으로 계산한 표시일 뿐 체크인 상태가 아니다.
- 취소 정책은 기존 이력 화면의 **이용 시작 전**을 적용했다. 정확히 시작시각이면 불가하다. Backlog 원문이 있다면 취소 허용 상태와 마감 시각을 팀과 재확인해야 한다.
- 모든 조회·취소 경로에서 서비스가 `APP_MEMBERS.status='ACTIVE'`와 `role='ROLE_STUDENT'`를 확인한다. 로그인도 `PENDING` 계정에 토큰을 발급하지 않지만, 이 서비스 검사는 기존 토큰을 가진 계정의 상태가 바뀐 경우까지 막는다.
- `SecurityConfig`는 현재 대부분의 경로에 역할별 규칙 대신 인증 여부만 확인한다. 이번 보호는 US-06·09의 설계 경로와 호환 경로에 적용한 것이다. 좌석 조회·예약 생성 등 다른 학생 전용 API의 역할 정책은 담당 팀원과 별도로 맞춰야 한다.

## 독립 데모 DB 준비

1. 운영 DB와 분리된 Oracle DB를 준비하고 팀의 실제 DDL로 `APP_MEMBERS`, `seats`, `reservations`를 구성한다. 학번 202402663의 실계정 비밀번호나 토큰을 시연 자료에 넣지 않는다.
2. **지정된 개발·테스트 DB에서만** 스키마 소유자와 `RESERVATIONS` 열 이름·자료형·제약을 먼저 확인한다. 기존 스키마에 `cancelled_at`, `cancelled_by`가 없다면 `demo/db/migrations/V1__reservation_student_cancel.sql`을 적용한다. 스크립트는 없는 열만 추가하므로 재실행할 수 있고 기존 행을 UPDATE/DELETE하지 않는다. 기존 열이 `DATE`/일반 `TIMESTAMP`, `STUDENT`를 저장할 수 있는 문자형이 아니면 오류로 중단한다. Oracle DDL은 자동 커밋되므로 첫 열 추가 후 두 번째 열 검사에서 오류가 나면 첫 열은 남는다. 원인을 수정한 다음 다시 실행한다. 실행 전후 `USER_TAB_COLUMNS`의 열 이름·자료형과 `reservations` 행 수를 비교한다. 실제 Oracle 적용은 아직 하지 않았다.
3. 학생 A와 학생 B, 활성 좌석 2개를 준비한다. A의 `RESERVED` 예정 예약 2개(하나는 취소 시연용), 지난 `RESERVED` 1개, `CHECKED_IN` 1개, `CANCELLED` 1개를 독립 DB에 마련한다. B의 예약도 1개 마련해 A 화면에 섞이지 않는지 확인한다. 지난 데이터는 일반 예약 화면이 지난 시간을 거부하므로 데모 DB의 승인된 seed 절차나 팀 DDL에 맞춘 SQL로만 넣는다.
4. 시연 전 `no_show_count`와 이용 제한 레코드 수를 기록하고, A의 예정 예약 1개와 같은 좌석·시간대의 좌석 상태를 기록한다. 실제 계정 ID, 좌석 ID, 시간은 데모 DB에서 조회해 정한다. 운영 데이터는 변경하지 않는다.

## 시연 순서 (팀 메모, PPT 제외)

| 입력 | 예상 화면·응답 | 확인 조건 |
| --- | --- | --- |
| A로 로그인 → 내 예약 이력 | 로딩 후 예정·지난·취소·체크인 기록, 좌석·날짜·시간·상태가 이용 시작 최신순으로 표시 | 인증된 A의 전체 이력만 조회 |
| 상태 필터와 정렬 전환 | 선택한 상태만 표시, 오래된순 전환 | 기존 US-07 인터페이스 유지 |
| A의 예정 예약 취소 → 확인 | 버튼 처리 중 비활성화, 취소 완료 후 목록에 `CANCELLED` 표시 | 저장 상태·`cancelled_at`·`cancelled_by=STUDENT` 확인 |
| 같은 시간대 좌석 화면 재조회 | 해당 좌석이 예약 가능으로 표시 | 취소 상태가 점유 쿼리에서 제외되고 SSE 이벤트 발행 |
| 예약 없는 별도 학생으로 로그인 | `아직 예약 이력이 없습니다` | 정상 빈 목록과 로딩·오류 화면 구분 |

예외 시연은 별도 데모 데이터로 (1) A가 B의 예약 ID로 취소 요청하면 404, (2) 같은 예약 재취소면 409, (3) 시작시각과 같거나 지난 예약이면 409를 권장한다. 미인증 조회·취소는 401이다. A의 노쇼 횟수·제한 상태가 취소 전후 같아야 한다.

## IDE에서 열 파일과 설명 (1~2분)

- `ReservationController.my/cancel`과 `ReservationHistoryController`: JWT subject에서 회원 ID를 얻고, 설계 경로와 과거 경로 모두 같은 서비스를 호출한다.
- `ReservationService.getMyReservations/cancel`: 조회는 인증된 ID만 전달한다. 취소 성공 시 좌석 변경 이벤트를 발행한다.
- `ReservationRepository.findHistory/cancelIfAllowed`: 소유자·상태·시작시각을 SQL 조건으로 확인하고 `CANCELLED`, 취소시각, 학생 취소자를 한 번에 저장한다.
- `ReservationHistory.jsx`: 저장 상태와 시간 기반 표시를 분리하고, 확인창과 처리 중 버튼 잠금을 제공한다. 취소 성공 후 화면 목록의 해당 항목을 갱신한다.

설명 대본: “학생이 내역을 열면 서버는 토큰의 subject를 사용합니다. 화면에서 보낸 회원 ID를 믿으면 다른 학생 예약을 읽을 수 있기 때문입니다. 취소도 ID만으로 처리하지 않고 DB의 한 UPDATE에서 소유자, RESERVED 상태, 시작 전 조건을 동시에 확인합니다. 그래서 두 요청이 겹쳐도 하나만 성공하고, 다른 경로로 시간 제한을 우회할 수 없습니다. 취소된 행은 삭제하지 않아 이력이 남습니다. 좌석 점유 조회는 RESERVED와 CHECKED_IN만 세므로 CANCELLED가 된 시간대는 다시 예약할 수 있고, 커밋 후 SSE로 좌석 화면에 변경을 알립니다. 화면에서 지난 예약이라는 표시는 시간 계산일 뿐 QR 체크인 완료를 의미하지 않습니다.”

이 변경과 SQL 검증 스크립트, Java 테스트는 이번 작업에서 생성·수정했다. 작성자가 실제로 수행한 수동 Oracle 시연, Java 테스트 실행, 프런트 빌드로 표현하면 안 된다. 평가 안내에 따라 PPT에는 주제·기술 스택과 팀원별 실제 Git 기여 이력만 필수로 넣고, 설계 차이와 미완료 사항이 있다면 추가한다. 이 시연 메모와 코드 설명은 PPT에 넣지 않는다.

## 실행·검증 기록

- `python3 -B -m unittest discover -s demo/tests -v`: 3개 통과. 저장소의 실제 SQL을 SQLite 메모리 DB에 실행해 본인 조회, 빈 목록, 과거·취소 목록, 활성 학생 역할·상태, 시작 직전·정각·직후, 반복 취소, 취소 기록, 노쇼 카운트 불변, 점유 해제를 확인했다. Oracle/JDBC 동시성 검증은 아니다.
- Java Mockito 테스트 파일은 작성했지만 저장소에 빌드 설정과 wrapper가 없어 실행하지 못했다. 프런트에도 `package.json`, Vite 설정, HTML 진입점이 없어 빌드하지 못했다.
- `./gradlew test` (`demo`): exit 127, Wrapper 없음. `npm run build --prefix vite-project`: exit 254, `package.json` 없음. `git diff --check`: 통과.
- 로컬 Gradle 캐시의 JAR 전체를 임시 classpath로 모아 `javac`을 실행하려 했으나 캐시 탐색이 장시간 진행되어 중단했다. Java 컴파일 성공으로 기록하지 않는다.
- Oracle 접속과 실제 스키마 확인, HTTP 인증 통합 테스트, 동시 요청, SSE 수신, 프런트 빌드는 환경 준비 후 검증해야 한다. 실제 실행 명령은 팀의 빌드 설정·Oracle 연결 정보를 확인한 뒤 확정한다.
- 세 원격 브랜치와 추적 이력에도 실행 설정이 없다. 팀에서 받아야 할 것: 백엔드 `build.gradle` 또는 `pom.xml`과 버전이 명시된 설정, Gradle이면 `settings.gradle`, `gradlew`/`gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`와 JAR, 프런트 `package.json`·잠금 파일·`index.html`·Vite 설정, 실제 Oracle DDL과 마이그레이션 이력, 비밀정보를 제외한 실행 환경 변수 안내, 지정된 개발·테스트 DB 및 테스트 계정, Product Backlog 원문 AC. 파일을 임의 버전으로 재구성하지 않았다.
- 인증·화면·SSE 연결은 코드를 추적해 확인했다. `SecurityConfig`는 미인증 요청을 401로 거절하고, 내역·취소 서비스는 활성 학생만 허용한다. `ReservationService.cancel`은 트랜잭션에서 이벤트를 발행하고 `SeatStatusBroadcaster`는 `AFTER_COMMIT`에만 `seat-changed`를 보낸다. `GET /api/seats/stream` → 프런트 `subscribeSeatChanges` → 좌석 재조회가 연결되어 있다. 실제 HTTP·브라우저·롤백 시연은 서버가 없어 미검증이다.
- 현재 저장소에는 US-11 노쇼 스케줄러·패널티 갱신 코드가 없다. 학생 취소 UPDATE는 `status='RESERVED'` 조건으로 한 번만 성공하고 노쇼 필드를 건드리지 않는다. 향후 노쇼 작업도 `RESERVED` 조건부 갱신이 성공한 행에만 같은 트랜잭션에서 패널티를 적용해야 하며, 취소와의 실제 경쟁 테스트는 US-11 구현 후 필요하다.
