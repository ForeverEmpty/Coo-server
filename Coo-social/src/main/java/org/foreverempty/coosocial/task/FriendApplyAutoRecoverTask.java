package org.foreverempty.coosocial.task;

import lombok.extern.slf4j.Slf4j;
import org.foreverempty.coosocial.service.FriendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FriendApplyAutoRecoverTask {

    @Autowired
    private FriendService friendService;

    @Scheduled(cron = "0 0 * * * *")
    public void recoverExpiredIgnoredApplies() {
        int updated = friendService.recoverExpiredIgnoredApplies();
        if (updated > 0) {
            log.info("Recovered expired ignored friend applies, count={}", updated);
        }
    }
}
