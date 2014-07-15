package ca.corefacility.bioinformatics.irida.ria.integration.pages;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import ca.corefacility.bioinformatics.irida.ria.integration.utilities.Ajax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Page Object to represent the projects page.
 * </p>
 * 
 * @author Josh Adam <josh.adam@phac-aspc.gc.ca>
 */
public class ProjectsPage {

	private WebDriver driver;
    private static final Logger logger = LoggerFactory.getLogger(ProjectsPage.class);

    public ProjectsPage(WebDriver driver) {
		this.driver = driver;
		driver.get("http://localhost:8080/projects");
		waitForAjax();
	}

	public int projectsTableSize() {
		WebElement element = driver.findElement(By.xpath("//table[@id='projectsTable']/tbody"));
		return element.findElements(By.tagName("tr")).size();
	}

	public WebElement getCollaboratorSpan() {
		WebElement el = driver.findElement(By.xpath("//table[@id='projectsTable']/tbody/tr[2]/td[4]/span"));
        return el;
	}

	public WebElement getOwnerSpan() {
		return driver.findElement(By.xpath("//table[@id='projectsTable']/tbody/tr[1]/td[4]/span"));
	}

	public List<WebElement> getProjectColumn() {
		waitForAjax();
		return driver.findElements(By.xpath("//table[@id='projectsTable']/tbody//td[2]"));
	}

	public void clickProjectNameHeader(){
		WebElement header = driver.findElement(By.xpath("//table[@id='projectsTable']/thead/tr/th[2]"));
		header.click();
		waitForAjax();
	}

	private void waitForAjax() {
		Wait<WebDriver> wait = new WebDriverWait(driver, 60);
		wait.until(Ajax.waitForAjax(60000));
	}
}
