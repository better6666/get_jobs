package com.getjobs.worker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 全平台投递任务排队器。
 *
 * 四个平台共享同一个浏览器上下文，并发执行会互相破坏对方的 Playwright 对象
 * （表现为 Object doesn't exist / Cannot find object to call __adopt__），
 * 因此同一时间只允许一个平台实际执行，其余平台自动排队轮候。
 */
@Component
@Slf4j
public class DeliveryTaskQueue {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "delivery-queue");
        t.setDaemon(true);
        return t;
    });

    /** 各平台是否有任务在排队/执行中 */
    private final Map<String, Boolean> queued = new ConcurrentHashMap<>();

    /**
     * 提交投递任务到队列
     *
     * @param platform 平台名 boss/liepin/51job/zhilian
     * @param task     实际投递逻辑（含 bringToFront）
     * @return true=已排队；false=该平台已有任务在队列中
     */
    public boolean submit(String platform, Runnable task) {
        if (Boolean.TRUE.equals(queued.get(platform))) {
            return false;
        }
        queued.put(platform, true);
        executor.execute(() -> {
            try {
                log.info("[{}] 轮到执行位，开始投递", platform);
                task.run();
            } catch (Exception e) {
                log.error("[{}] 投递任务执行异常: {}", platform, e.getMessage(), e);
            } finally {
                queued.remove(platform);
            }
        });
        return true;
    }

    /**
     * 该平台是否已有任务在排队或执行中（不含服务自身 isRunning 判断）
     */
    public boolean isQueued(String platform) {
        return Boolean.TRUE.equals(queued.get(platform));
    }
}
