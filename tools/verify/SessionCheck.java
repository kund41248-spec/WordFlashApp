import java.util.*;

/**
 * StudySessionManager 队列推进逻辑的对应实现，用于在真实 JVM 上验证：
 *  1. 答错重现不会导致无限循环（必须有终止条件）
 *  2. 正确率 / 新学数 / 复习数的统计口径
 *  3. currentIndex 与 cards.size 的关系
 *
 * 注意：这是对 Kotlin 版**队列逻辑**的独立复刻，
 * 用于验证算法不变量；不覆盖 Room/协程部分。
 */
public class SessionCheck {

    static int passed = 0, failed = 0;

    static void check(String name, Object actual, Object expected) {
        boolean ok = String.valueOf(actual).equals(String.valueOf(expected));
        if (ok) { passed++; System.out.printf("  PASS  %-48s = %s%n", name, actual); }
        else { failed++; System.out.printf("  FAIL  %-48s  实际=%s  期望=%s%n", name, actual, expected); }
    }

    /** 模拟会话状态 */
    static class Session {
        List<String> cards = new ArrayList<>();
        int index = 0, correct = 0, answered = 0;
        boolean repeatWrongAtEnd;
        Set<String> seen = new LinkedHashSet<>();
        Set<String> newWords = new LinkedHashSet<>();
        Set<String> wrong = new LinkedHashSet<>();
        Map<String, Integer> repeatCount = new HashMap<>();
        static final int MAX_REPEAT_PER_WORD = 2;

        Session(List<String> initial, boolean repeatWrong) {
            cards = new ArrayList<>(initial);
            repeatWrongAtEnd = repeatWrong;
            // 初始卡片：标记 seen / new（假设前一半是新词）
            for (int i = 0; i < initial.size(); i++) {
                seen.add(initial.get(i));
                if (i < initial.size() / 2) newWords.add(initial.get(i));
            }
        }

        boolean hasCurrent() { return index < cards.size(); }
        String current() { return hasCurrent() ? cards.get(index) : null; }

        void answer(boolean isCorrect) {
            if (!hasCurrent()) return;
            String card = current();
            if (!isCorrect) {
                wrong.add(card);
                if (repeatWrongAtEnd) {
                    int t = repeatCount.getOrDefault(card, 0);
                    if (t < MAX_REPEAT_PER_WORD) {
                        repeatCount.put(card, t + 1);
                        cards.add(card);
                    }
                }
            } else {
                correct++;
            }
            answered++;
            index++;
        }

        boolean finished() { return index >= cards.size(); }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 正常答对：队列不增长 ===");
        Session s1 = new Session(Arrays.asList("a", "b", "c", "d"), true);
        check("初始题数", s1.cards.size(), 4);
        while (s1.hasCurrent()) s1.answer(true);
        check("全部答对后队列仍为 4", s1.cards.size(), 4);
        check("answered", s1.answered, 4);
        check("correct", s1.correct, 4);
        check("已结束", s1.finished(), true);

        System.out.println("\n=== 2. 答错重现：队列增长，但每次答对后收敛 ===");
        Session s2 = new Session(Arrays.asList("a", "b"), true);
        s2.answer(false);                        // a 答错 -> 追加 a
        check("答错后队列长度", s2.cards.size(), 3);
        check("追加的是同一个词", s2.cards.get(2), "a");
        s2.answer(true);                         // b 答对
        s2.answer(true);                         // a 重现已答对
        check("重现后答对，队列不再增长", s2.cards.size(), 3);
        check("已结束", s2.finished(), true);
        check("总题数 3", s2.answered, 3);
        check("答对数 2", s2.correct, 2);

        System.out.println("\n=== 3. 对抗测试：全部答错，队列也必须终止 ===");
        Session s3 = new Session(Arrays.asList("x"), true);
        int guard = 0;
        while (s3.hasCurrent() && guard++ < 1000) {
            s3.answer(false);           // 永远答错
        }
        check("循环未失控（guard<1000）", guard < 1000, true);
        check("最终已结束", s3.finished(), true);
        // 1 个初始 + 2 次重现 = 3 题，之后不再追加
        check("总题数被上限截断为 3", s3.answered, 3);
        check("队列长度 3", s3.cards.size(), 3);

        System.out.println("\n=== 3b. 多词全部答错的队列上界 ===");
        Session s3b = new Session(Arrays.asList("a", "b", "c", "d", "e"), true);
        guard = 0;
        while (s3b.hasCurrent() && guard++ < 1000) s3b.answer(false);
        // 5 初始 + 每词最多重现 2 次 = 最多 15
        check("题数不超过 5*(1+2)=15", s3b.answered <= 15, true);
        check("题数恰好为 15", s3b.answered, 15);
        check("已结束", s3b.finished(), true);

        System.out.println("\n=== 4. 关闭重现时，答错不再追加 ===");
        Session s4 = new Session(Arrays.asList("p", "q"), false);
        s4.answer(false);
        check("队列长度不变", s4.cards.size(), 2);
        s4.answer(false);
        check("已结束", s4.finished(), true);
        check("correct 为 0", s4.correct, 0);

        System.out.println("\n=== 5. 统计口径：新学 / 复习 去重 ===");
        Session s5 = new Session(Arrays.asList("n1", "n2", "r1", "r2"), true);
        // 模拟 n1 答错后重现
        s5.answer(false);   // n1 错
        s5.answer(true);    // n2 对
        s5.answer(true);    // r1 对
        s5.answer(true);    // r2 对
        s5.answer(true);    // n1 重现，对
        check("seen 去重后 4 个词", s5.seen.size(), 4);
        check("newWords 2 个", s5.newWords.size(), 2);
        check("复习数 = seen - new", s5.seen.size() - s5.newWords.size(), 2);
        check("总题数 5（含重现）", s5.answered, 5);

        System.out.println("\n=== 6. 进度条边界 ===");
        Session s6 = new Session(Arrays.asList("a", "b", "c"), true);
        check("起始 index=0", s6.index, 0);
        s6.answer(true);
        check("答一题后 index=1", s6.index, 1);
        float prog = (float) s6.index / s6.cards.size();
        check("进度 1/3", String.format("%.4f", prog), "0.3333");

        System.out.printf("%n================ 结果: %d 通过, %d 失败 ================%n", passed, failed);
        if (failed > 0) System.exit(1);
    }
}
