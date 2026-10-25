package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LayoutStoreTest {

    private static final Layout LAYOUT = new Layout("unknown-bank.csv", Layout.Kind.BANK, ';', 0, "dd/MM/yyyy", null, 1, 2, null,
            null, 3, "D", null, List.of("PENDENTE"), null, false, Layout.AmountFormat.DECIMAL_COMMA);

    @Test
    void learnThenLoadRoundTripsAndIsReadPerRun(@TempDir Path work) {
        LayoutStore store = new LayoutStore(Fixtures.properties(work));
        assertThat(store.load()).isEmpty();

        store.learn("abcd1234", LAYOUT);

        assertThat(store.load()).containsEntry("abcd1234", LAYOUT.withName("learned-abcd1234"));
        assertThat(new LayoutStore(Fixtures.properties(work)).load()).hasSize(1);
    }

    @Test
    void learnRewritesWithoutDuplicates(@TempDir Path work) throws Exception {
        LayoutStore store = new LayoutStore(Fixtures.properties(work));

        store.learn("abcd1234", LAYOUT);
        store.learn("abcd1234", LAYOUT.withAmounts(Layout.AmountFormat.DECIMAL_DOT));

        assertThat(Files.readAllLines(work.resolve("layouts-learned.csv"))).hasSize(2);
        assertThat(store.load().get("abcd1234").amounts()).isEqualTo(Layout.AmountFormat.DECIMAL_DOT);
    }

    @Test
    void twoThreadsLearningAtOnceLeaveAParseableFile(@TempDir Path work) throws Exception {
        LayoutStore store = new LayoutStore(Fixtures.properties(work));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 20; i++) {
            String fingerprint = "fp%02d".formatted(i);
            pool.submit(() -> store.learn(fingerprint, LAYOUT));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(store.load()).hasSize(20);
    }
}
