package com.example.demo.seat;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * GET /api/seats/stream 구독자에게 좌석 상태 변경을 실시간으로 알린다 (설계서 p.11).
 * 이벤트에는 바뀐 좌석의 층·날짜만 담고, 화면은 자기 조건과 맞으면 GET /api/seats 를 다시 조회한다.
 */
@Component
public class SeatStatusBroadcaster {
    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public record SeatChange(long seat_id, int floor, String date) {}

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));
        try {
            // 연결 직후 한 번 보내 응답 헤더를 바로 내려보낸다.
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** 예약 트랜잭션이 커밋된 뒤에만 알린다 (롤백된 예약은 알리지 않음). */
    @TransactionalEventListener(fallbackExecution = true)
    public void onSeatStatusChanged(SeatStatusChangedEvent event) {
        SeatChange change = new SeatChange(event.seatId(), event.floor(), event.date().toString());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("seat-changed").data(change, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    int subscriberCount() {
        return emitters.size();
    }
}
