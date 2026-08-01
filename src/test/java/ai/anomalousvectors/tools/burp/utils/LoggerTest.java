package ai.anomalousvectors.tools.burp.utils;

import burp.api.montoya.logging.Logging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoggerTest {

    @Mock
    private Logging logging;

    @AfterEach
    void tearDown() {
        // no public reset; remove sinks registered in tests via removeSink
    }

    @Test
    void logInfo_writesToBurpAndSinks() {
        List<String> infos = new ArrayList<>();
        Logger.Sink sink = new Logger.Sink() {
            @Override
            public void info(String msg) {
                infos.add(msg);
            }

            @Override
            public void error(String msg) {
            }
        };

        Logger.initialize(logging);
        Logger.addSink(sink);
        try {
            Logger.logInfo("hello");
            verify(logging).logToOutput("hello");
            assertThat(infos).containsExactly("hello");
        } finally {
            Logger.removeSink(sink);
        }
    }

    @Test
    void logError_writesToBurpError() {
        Logger.initialize(logging);
        Logger.logError("boom");
        verify(logging).logToError("boom");
    }
}
