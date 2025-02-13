package com.github.mizool.core.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.util.Strings;

public class TestSynchronizer
{
    private static final Predicate<Character> IS_LETTER = Character::isLetter;
    private static final Predicate<Character> IS_DIGIT = Character::isDigit;

    @RequiredArgsConstructor
    @Builder
    private static class Appender implements Runnable
    {
        private final Synchronizer synchronizer;
        private final StringBuilder target;
        private final int limit;
        private final Predicate<Character> predicate;
        private final char charToAppend;

        @Override
        public void run()
        {
            boolean keepRunning;
            do
            {
                keepRunning = synchronizer.sleepUntil(this::shouldWake)
                    .get(this::doAppend)
                    .andWakeOthers()
                    .invoke();
            }
            while (keepRunning);
        }

        private boolean shouldWake()
        {
            if (target.length() == 0)
            {
                return false;
            }

            if (target.length() >= limit)
            {
                return true;
            }

            char lastChar = target.charAt(target.length() - 1);
            return predicate.test(lastChar);
        }

        private boolean doAppend()
        {
            if (target.length() >= limit)
            {
                // We're finished; let's end the loop in run().
                return false;
            }

            target.append(charToAppend);
            return true;
        }
    }

    private Synchronizer synchronizer;

    @BeforeMethod
    private void setUp()
    {
        synchronizer = new Synchronizer();
    }

    @DataProvider
    public static Object[][] pingPongVariants()
    {
        return new Object[][]{
            new Object[]{ 'a', "ab" }, new Object[]{ 'b', "ba" }
        };
    }

    /**
     * Two threads where each appends its letter ('a' or 'b') after the other one did.
     */
    @Test(timeOut = 2000, dataProvider = "pingPongVariants")
    public void testFairPingPong(char initialChar, String resultingPattern)
    {
        StringBuilder resultBuilder = new StringBuilder();

        var appenders = List.of(Appender.builder()
                .synchronizer(synchronizer)
                .target(resultBuilder)
                .limit(100)
                .predicate(previous -> previous == 'b')
                .charToAppend('a')
                .build(),
            Appender.builder()
                .synchronizer(synchronizer)
                .target(resultBuilder)
                .limit(100)
                .predicate(previous -> previous == 'a')
                .charToAppend('b')
                .build());

        runAppenderTest(synchronizer, resultBuilder, appenders, initialChar);

        String result = resultBuilder.toString();
        assertThat(result).isEqualTo(Strings.repeat(resultingPattern, 50));
    }

    private void runAppenderTest(
        Synchronizer synchronizer, StringBuilder target, List<Appender> appenders, char initialChar)
    {
        submitAndWaitForCompletion(() -> synchronizer.run(() -> target.append(initialChar))
            .andWakeOthers()
            .invoke(), appenders);
    }

    private void submitAndWaitForCompletion(Runnable initialAction, List<? extends Runnable> runnables)
    {
        CompletableFuture<Void> combinedFuture = submit(runnables);
        initialAction.run();
        Futures.get(combinedFuture);
    }

    public CompletableFuture<Void> submit(List<? extends Runnable> runnables)
    {
        ExecutorService executorService = Executors.newFixedThreadPool(runnables.size());

        var completableFutures = runnables.stream()
            .map(runnable -> CompletableFuture.runAsync(runnable, executorService))
            .collect(Collectors.toList())
            .toArray(new CompletableFuture<?>[]{});

        return CompletableFuture.allOf(completableFutures);
    }

    @DataProvider
    public static Object[][] unfairPingPongVariants()
    {
        return new Object[][]{
            new Object[]{ 'a', 'a', 'b', "ab" },
            new Object[]{ 'b', 'b', 'a', "ba" },
            new Object[]{ 'a', 'b', 'a', "ab" },
            new Object[]{ 'b', 'a', 'b', "ba" }
        };
    }

    /**
     * Like {@link #testFairPingPong(char, String)}, but with 50 threads appending the 'majority' char versus only one
     * that appends the 'minority' char.
     */
    @Test(timeOut = 2000, dataProvider = "unfairPingPongVariants")
    public void testUnfairPingPong(char initialChar, char majorityChar, char minorityChar, String resultingPattern)
    {
        StringBuilder resultBuilder = new StringBuilder();

        List<Appender> appenders = new ArrayList<>();

        for (int i = 0; i < 50; i++)
        {
            appenders.add(Appender.builder()
                .synchronizer(synchronizer)
                .target(resultBuilder)
                .limit(100)
                .predicate(previous -> previous != majorityChar)
                .charToAppend(majorityChar)
                .build());
        }

        appenders.add(Appender.builder()
            .synchronizer(synchronizer)
            .target(resultBuilder)
            .limit(100)
            .predicate(previous -> previous != minorityChar)
            .charToAppend(minorityChar)
            .build());

        runAppenderTest(synchronizer, resultBuilder, appenders, initialChar);

        String result = resultBuilder.toString();
        assertThat(result).isEqualTo(Strings.repeat(resultingPattern, 50));
    }

    @DataProvider
    public static Object[][] groupVariants()
    {
        return new Object[][]{
            new Object[]{ 'a', "letter", "digit", IS_LETTER, IS_DIGIT },
            new Object[]{ '1', "digit", "letter", IS_DIGIT, IS_LETTER },
            };
    }

    /**
     * Threads for each letter and for each digit together produce a string that strictly alternates between letters and
     * digits.
     */
    @Test(timeOut = 2000, dataProvider = "groupVariants")
    public void testTwoGroups(
        char initialCharacter,
        String firstKind,
        String secondKind,
        Predicate<Character> firstPredicate,
        Predicate<Character> secondPredicate)
    {
        StringBuilder resultBuilder = new StringBuilder();

        List<Appender> appenders = new ArrayList<>();

        for (char letter : "abcdefghijklmnopqrstuvwxyz".toCharArray())
        {
            appenders.add(Appender.builder()
                .synchronizer(synchronizer)
                .target(resultBuilder)
                .limit(100)
                .predicate(Character::isDigit)
                .charToAppend(letter)
                .build());
        }

        for (char digit : "0123456789".toCharArray())
        {
            appenders.add(Appender.builder()
                .synchronizer(synchronizer)
                .target(resultBuilder)
                .limit(100)
                .predicate(Character::isLetter)
                .charToAppend(digit)
                .build());
        }

        runAppenderTest(synchronizer, resultBuilder, appenders, initialCharacter);

        String result = resultBuilder.toString();
        System.out.println(result);

        // The characters in the string should strictly alternate between the two kinds of characters
        assertSoftly(softly -> {
            for (int i = 0; i < result.length(); i += 2)
            {
                softly.assertThat(result.charAt(i))
                    .describedAs("char #%d in \"%s\"", i, result)
                    .matches(firstPredicate, "is " + firstKind);

                softly.assertThat(result.charAt(i + 1))
                    .describedAs("char #%d in \"%s\"", i + 1, result)
                    .matches(secondPredicate, "is " + secondKind);
            }
        });
    }

    @Test(timeOut = 2000)
    public void testWakingAffectsAllThreads()
    {
        Set<Integer> integers = IntStream.range(0, 100)
            .boxed()
            .collect(Collectors.toSet());

        Set<Integer> chainsThatWoke = new HashSet<>();

        AtomicBoolean ready = new AtomicBoolean(false);

        List<Runnable> runnables = integers.stream()
            .map(value -> (Runnable) () -> synchronizer.sleepUntil(() -> {
                    /*
                     * As the condition is checked immediately when invoking the chain, we must return false to even
                     * begin sleeping.
                     */
                    if (!ready.get())
                    {
                        return false;
                    }

                    // Track that this chain woke to check its condition
                    chainsThatWoke.add(value);

                    return true;
                })
                .invoke())
            .collect(Collectors.toList());

        submitAndWaitForCompletion(() -> {
            ready.set(true);
            synchronizer.wakeOthers()
                .invoke();
        }, runnables);

        assertThat(chainsThatWoke).isEqualTo(integers);
    }

    @Test(timeOut = 2000)
    public void testFailingConditionPreventsMainAction()
    {
        AtomicBoolean conditionChecked = new AtomicBoolean(false);

        submit(() -> synchronizer.sleepUntil(() -> {
                // Track that this chain woke to check its condition
                conditionChecked.set(true);

                return false;
            })
            .run(() -> fail("Main action is not supposed to run when condition is false"))
            .invoke());

        synchronizer.wakeOthers()
            .invoke();

        Threads.sleep(200);

        assertThat(conditionChecked).isTrue();
    }

    private CompletableFuture<Void> submit(Runnable... runnables)
    {
        return submit(Arrays.asList(runnables));
    }

    @Test(timeOut = 2000)
    public void testConditionCheckedInitially()
    {
        AtomicBoolean conditionChecked = new AtomicBoolean(false);

        CompletableFuture<Void> future = submit(() -> synchronizer.sleepUntil(() -> {
                conditionChecked.set(true);
                return true;
            })
            .invoke());

        Futures.get(future, Duration.ofMillis(200));

        // Condition was checked even though no wake call happened
        assertThat(conditionChecked).isTrue();
    }

    @Test(timeOut = 2000)
    public void testConditionCheckInterval()
    {
        AtomicInteger conditionCheckCount = new AtomicInteger();
        AtomicBoolean finishing = new AtomicBoolean(false);

        Duration interval = Duration.ofMillis(200);
        int intervalCount = 2;

        CompletableFuture<Void> future = submit(() -> synchronizer.sleepUntil(() -> {
                conditionCheckCount.incrementAndGet();
                return finishing.get();
            }, interval)
            .invoke());

        Threads.sleep((long) (interval.toMillis() * (intervalCount + 0.5)));

        finishing.set(true);
        synchronizer.wakeOthers()
            .invoke();

        Futures.get(future, interval.dividedBy(4));

        /*
         * In addition to the interval count, there is one check shortly after invoking the chain and another one due to
         * the finishing wakeOthers() call.
         */
        assertThat(conditionCheckCount).hasValue(intervalCount + 2);
    }

    @Test(timeOut = 2000)
    public void testConditionallyWakingOthers()
    {
        AtomicBoolean ready = new AtomicBoolean(false);
        AtomicBoolean firstCompleted = new AtomicBoolean(false);
        AtomicBoolean secondCompleted = new AtomicBoolean(false);

        Runnable first = () -> synchronizer.sleepUntil(ready::get)
            .get(() -> {
                firstCompleted.set(true);
                return "indeed";
            })
            .andWakeOthersIf(s -> s.equals("indeed"))
            .invoke();

        Runnable second = () -> synchronizer.sleepUntil(() -> ready.get() && firstCompleted.get())
            .run(() -> secondCompleted.set(true))
            .invoke();

        submitAndWaitForCompletion(() -> {
            ready.set(true);
            synchronizer.wakeOthers()
                .invoke();
        }, first, second);

        assertThat(secondCompleted).isTrue();
    }

    private void submitAndWaitForCompletion(Runnable initialAction, Runnable... runnables)
    {
        submitAndWaitForCompletion(initialAction, Arrays.asList(runnables));
    }
}
