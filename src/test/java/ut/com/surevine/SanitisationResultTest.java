package ut.com.surevine;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.junit.Test;

import com.surevine.sanitsation.SanitisationResult;

public class SanitisationResultTest {

	@Test
	public void testIsSaneFalse() {
		SanitisationResult result = new SanitisationResult(new File("/tmp/test/path"), false);
		assertFalse(result.isSane());
	}

	@Test
	public void testIsSaneTrue() {
		SanitisationResult result = new SanitisationResult(new File("/tmp/test/path"), false);
		result.setSane(true);
		assertTrue(result.isSane());
	}

	@Test
	public void testGetErrors() {
		String errorMessage = "test error";
		SanitisationResult result = new SanitisationResult(new File("/tmp/test/path"), false);
		result.addError(errorMessage);
		List<String> errorMessages = result.getErrors();
		assertTrue(errorMessages.contains(errorMessage));
		assertFalse(errorMessages.contains("second message"));
	}

}
