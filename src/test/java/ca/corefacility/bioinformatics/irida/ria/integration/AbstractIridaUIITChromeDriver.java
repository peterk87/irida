package ca.corefacility.bioinformatics.irida.ria.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseTearDown;

import ca.corefacility.bioinformatics.irida.config.data.IridaApiJdbcDataSourceConfig;
import ca.corefacility.bioinformatics.irida.config.services.IridaApiPropertyPlaceholderConfig;
import ca.corefacility.bioinformatics.irida.ria.integration.pages.LoginPage;
import ca.corefacility.bioinformatics.irida.web.controller.test.listeners.IntegrationUITestListener;

/**
 * Common functionality to all UI integration tests.
 */
@ActiveProfiles("it")
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {IridaApiJdbcDataSourceConfig.class,
        IridaApiPropertyPlaceholderConfig.class})
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, DbUnitTestExecutionListener.class})
@DatabaseTearDown("classpath:/ca/corefacility/bioinformatics/irida/test/integration/TableReset.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AbstractIridaUIITChromeDriver {

    private static final Logger logger = LoggerFactory.getLogger(AbstractIridaUIITChromeDriver.class);

    public static final int DRIVER_TIMEOUT_IN_SECONDS = IntegrationUITestListener.DRIVER_TIMEOUT_IN_SECONDS;

    private static boolean isSingleTest = false;

    @Rule
    public ScreenshotOnFailureWatcher watcher = new ScreenshotOnFailureWatcher();

    /**
     * Code to execute before *each* test.
     */
    @Before
    public void setUpTest() throws IOException {
    	// logout before everything else.
    	LoginPage.logout(driver());
    }

    /**
     * Code to execute after *each* test.
     */
    @After
    public void tearDown() {
    	// NOTE: DO **NOT** log out in this method. This method happens because
    	// the @After method happens immediately after test failure, but before
    	// the @Rule TestWatcher checks the outcome of the test. We want to take
    	// a screenshot of the application state **before** logging out.
    }

    /**
     * Code to execute *once* after the class is finished.
     */
    @AfterClass
    public static void destroy() {
        if (isSingleTest) {
            logger.debug("Closing ChromeDriver for single test class.");
            IntegrationUITestListener.stopWebDriver();
        }
    }

    /**
     * Get a reference to the {@link WebDriver} used in the tests.
     * @return the instance of {@link WebDriver} used in the tests.
     */
    public static WebDriver driver() {
        if (IntegrationUITestListener.driver() == null) {
            logger.debug("Starting ChromeDriver for a single test class.");
            System.setProperty("webdriver.chrome.driver", "src/main/webapp/node_modules/chromedriver/lib/chromedriver/chromedriver");
            isSingleTest = true;
            IntegrationUITestListener.startWebDriver();
        }

        return IntegrationUITestListener.driver();

    }

    /**
     * Simple test watcher for taking screenshots of the browser on failure.
     *
     */
    private static class ScreenshotOnFailureWatcher extends TestWatcher {

    	private static final Logger logger = LoggerFactory.getLogger(ScreenshotOnFailureWatcher.class);

    	/**
    	 * {@inheritDoc}
    	 */
    	@Override
    	protected void failed(final Throwable t, final Description description) {
			logger.debug("Handling exception of type [" + t.getClass() + "], taking screenshot: " + t.getMessage(), t);
    		final TakesScreenshot takesScreenshot = (TakesScreenshot) driver();

    		final Path screenshot = Paths.get(takesScreenshot.getScreenshotAs(OutputType.FILE).toURI());

    		try {
				final Path destination = Files.createTempFile(
						"irida-" + description.getTestClass().getSimpleName() + "#" + description.getMethodName(),
						".png");
    			Files.move(screenshot, destination, StandardCopyOption.REPLACE_EXISTING);
    			logger.info("Screenshot deposited at: [" + destination.toString() + "]");
    		} catch (final IOException e) {
    			logger.error("Unable to write screenshot out.", e);
    		}
    	}
    }
}
