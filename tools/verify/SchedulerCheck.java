import java.util.*;

/**
 * EbbinghausScheduler 的 Java 逐行对应实现，仅用于在真实 JVM 上验证算术语义。
 * 逻辑与 Kotlin 版必须完全一致；若两者不符，说明 Kotlin 版有问题。
 */
public class SchedulerCheck {

    static final int[] INTERVALS_DAYS = {0, 1, 2, 4, 7, 15, 30, 60, 180};
    static final float MIN_EASE = 1.3f, MAX_EASE = 2.8f, DEFAULT_EASE = 2.5f;
    static final int REP_TO_REVIEWING = 2;
    static final int MASTERED_INTERVAL = 60;
    static final long RELEARN_DELAY_MS = 10L * 60 * 1000;
    static final long DAY_MS = 24L * 60 * 60 * 1000;

    // 状态常量，对应 WordState
    static final int NEW = 0, LEARNING = 1, REVIEWING = 2, MASTERED = 3;

    static class P {
        int state = NEW, repetitions = 0, intervalDays = 0;
        float easeFactor = DEFAULT_EASE;
        long nextReviewAt = 0, lastReviewAt = 0;
        int correctCount = 0, wrongCount = 0, reviewCount = 0;
        P copy() {
            P p = new P();
            p.state = state; p.repetitions = repetitions; p.intervalDays = intervalDays;
            p.easeFactor = easeFactor; p.nextReviewAt = nextReviewAt;
            p.lastReviewAt = lastReviewAt; p.correctCount = correctCount;
            p.wrongCount = wrongCount; p.reviewCount = reviewCount;
            return p;
        }
    }

    static P schedule(P cur, boolean correct, long now) {
        P base = (cur == null) ? new P() : cur;
        return correct ? onCorrect(base, now) : onWrong(base, now);
    }

    static P onCorrect(P p, long now) {
        int repetitions = p.repetitions + 1;
        int idx = Math.min(repetitions, INTERVALS_DAYS.length - 1);
        int intervalDays = INTERVALS_DAYS[idx];
        float newEase = Math.min(p.easeFactor + 0.1f, MAX_EASE);

        int effectiveDays = Math.max(1, Math.min((int) (intervalDays * newEase / DEFAULT_EASE), intervalDays));

        int newState;
        if (effectiveDays >= MASTERED_INTERVAL) newState = MASTERED;
        else if (repetitions >= REP_TO_REVIEWING) newState = REVIEWING;
        else newState = LEARNING;

        P r = p.copy();
        r.state = newState; r.repetitions = repetitions; r.intervalDays = effectiveDays;
        r.easeFactor = newEase; r.nextReviewAt = now + effectiveDays * DAY_MS;
        r.lastReviewAt = now; r.correctCount++; r.reviewCount++;
        return r;
    }

    static P onWrong(P p, long now) {
        float newEase = Math.max(p.easeFactor - 0.2f, MIN_EASE);
        P r = p.copy();
        r.state = LEARNING; r.repetitions = 0; r.intervalDays = 0; r.easeFactor = newEase;
        r.nextReviewAt = now + RELEARN_DELAY_MS; r.lastReviewAt = now;
        r.wrongCount++; r.reviewCount++;
        return r;
    }

    // ---------------- 测试 ----------------
    static int passed = 0, failed = 0;

    static void check(String name, Object actual, Object expected) {
        boolean ok = String.valueOf(actual).equals(String.valueOf(expected));
        if (ok) { passed++; System.out.printf("  PASS  %-46s = %s%n", name, actual); }
        else { failed++; System.out.printf("  FAIL  %-46s  实际=%s  期望=%s%n", name, actual, expected); }
    }

    static String stateName(int s) {
        switch (s) {
            case NEW: return "NEW";
            case LEARNING: return "LEARNING";
            case REVIEWING: return "REVIEWING";
            case MASTERED: return "MASTERED";
            default: return "?";
        }
    }

    public static void main(String[] args) {
        long DAY0 = 1_700_000_000_000L;   // 固定基准时间，保证可复现

        System.out.println("=== 1. 新词首次答对 ===");
        P a = schedule(null, true, DAY0);
        check("state", stateName(a.state), "LEARNING");
        check("repetitions", a.repetitions, 1);
        check("easeFactor", a.easeFactor, 2.6f);
        check("intervalDays (1*2.6/2.5=1)", a.intervalDays, 1);
        check("correctCount", a.correctCount, 1);
        check("wrongCount", a.wrongCount, 0);
        check("nextReviewAt = now+1天", a.nextReviewAt, DAY0 + DAY_MS);

        System.out.println("\n=== 2. 新词首次答错 ===");
        P b = schedule(null, false, DAY0);
        check("state", stateName(b.state), "LEARNING");
        check("repetitions", b.repetitions, 0);
        check("easeFactor (2.5-0.2)", b.easeFactor, 2.3f);
        check("intervalDays", b.intervalDays, 0);
        check("wrongCount", b.wrongCount, 1);
        check("nextReviewAt = now+10分钟", b.nextReviewAt, DAY0 + RELEARN_DELAY_MS);

        System.out.println("\n=== 3. 连续答对：状态与间隔推进 ===");
        P p = null;
        long t = DAY0;
        int[] expectedInterval = {1, 2, 4, 7, 15, 30, 60, 180};
        String[] expectedState = {"LEARNING", "REVIEWING", "REVIEWING",
                                  "REVIEWING", "REVIEWING", "REVIEWING",
                                  "MASTERED", "MASTERED"};
        for (int i = 0; i < 8; i++) {
            p = schedule(p, true, t);
            t = p.nextReviewAt;   // 到期即复习
            check("第" + (i + 1) + "次答对 intervalDays", p.intervalDays, expectedInterval[i]);
            check("第" + (i + 1) + "次答对 state", stateName(p.state), expectedState[i]);
        }
        check("累计答对次数", p.correctCount, 8);
        check("EF 达到上限 2.8", p.easeFactor, 2.8f);
        check("repetitions 封顶在序列末位", p.repetitions, 8);

        System.out.println("\n=== 4. 答错重置，但 EF 保留（关键行为）===");
        P q = new P();
        q = schedule(q, true, DAY0); q = schedule(q, true, q.nextReviewAt);
        q = schedule(q, true, q.nextReviewAt);
        check("三次答对后 repetitions", q.repetitions, 3);
        check("三次答对后 intervalDays", q.intervalDays, 4);
        float easeBefore = q.easeFactor;
        P q2 = schedule(q, false, DAY0);
        check("答错后 repetitions 归零", q2.repetitions, 0);
        check("答错后 intervalDays 归零", q2.intervalDays, 0);
        check("答错后 state 回到 LEARNING", stateName(q2.state), "LEARNING");
        check("答错后 EF 下降 0.2", q2.easeFactor, easeBefore - 0.2f);
        check("答错不清空历史答对计数", q2.correctCount, 3);
        check("答错累加错误计数", q2.wrongCount, 1);

        System.out.println("\n=== 5. EF 下界保护（反复答错不会跌破 1.3）===");
        P r = null;
        for (int i = 0; i < 15; i++) r = schedule(r, false, DAY0);
        check("连续 15 次答错后 EF 仍为下界", r.easeFactor, MIN_EASE);
        check("错误计数正确累加", r.wrongCount, 15);

        System.out.println("\n=== 6. 难度差异：EF 低于基准的词，间隔应更短 ===");
        // 易词：一路答对，EF 升到 2.8
        P easy = null;
        for (int i = 0; i < 5; i++) easy = schedule(easy, true, DAY0);
        // 难词：反复答错把 EF 压到 2.5 以下，再连对 5 次
        // （EF 只在答对时 +0.1，因此需要足够多的错误把基准压低）
        P hard = null;
        for (int i = 0; i < 6; i++) hard = schedule(hard, false, DAY0);
        for (int i = 0; i < 5; i++) hard = schedule(hard, true, DAY0);
        System.out.printf("  易词 EF=%.2f interval=%d 天%n", easy.easeFactor, easy.intervalDays);
        System.out.printf("  难词 EF=%.2f interval=%d 天%n", hard.easeFactor, hard.intervalDays);
        check("难词 EF 低于 2.5 基准", hard.easeFactor < DEFAULT_EASE, true);
        check("难词间隔严格短于易词", hard.intervalDays < easy.intervalDays, true);

        System.out.println("\n=== 6b. 间隔永不超过序列值（防止双重增长）===");
        P cap = null;
        boolean withinSequence = true;
        for (int i = 0; i < 8; i++) {
            cap = schedule(cap, true, DAY0);
            if (cap.intervalDays > INTERVALS_DAYS[Math.min(i + 1, INTERVALS_DAYS.length - 1)]) {
                withinSequence = false;
            }
        }
        check("八次答对间隔始终不超过序列值", withinSequence, true);
        check("末次间隔恰为序列上限 180", cap.intervalDays, 180);

        System.out.println("\n=== 7. 到期判断 isDue ===");
        P n = new P();
        check("null 进度视为到期", n.nextReviewAt <= DAY0, true);
        P f = schedule(null, true, DAY0);
        check("刚答对未到期", f.nextReviewAt <= DAY0, false);
        check("超过到期时间后到期", f.nextReviewAt <= DAY0 + DAY_MS + 1, true);

        System.out.println("\n=== 8. 边界：intervalDays 永不为 0（答对路径）===");
        P e = null;
        boolean allPositive = true;
        for (int i = 0; i < 10; i++) {
            e = schedule(e, true, DAY0);
            if (e.intervalDays < 1) allPositive = false;
        }
        check("十次答对的 intervalDays 均 >= 1", allPositive, true);

        System.out.printf("%n================ 结果: %d 通过, %d 失败 ================%n", passed, failed);
        if (failed > 0) System.exit(1);
    }
}
