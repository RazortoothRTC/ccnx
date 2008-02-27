/**
 * 
 */
package test.ccn.library;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Iterator;

import junit.framework.Assert;

import org.junit.Test;

import com.parc.ccn.CCNBase;
import com.parc.ccn.data.CompleteName;
import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.ContentObject;
import com.parc.ccn.data.MalformedContentNameStringException;
import com.parc.ccn.data.query.BasicQueryListener;
import com.parc.ccn.data.query.CCNQueryDescriptor;
import com.parc.ccn.data.query.Interest;
import com.parc.ccn.data.security.PublisherID;
import com.parc.ccn.library.CCNLibrary;
import com.parc.ccn.library.StandardCCNLibrary;
import com.parc.ccn.network.CCNRepositoryManager;


/**
 * @author briggs
 *
 */
public class StandardCCNLibraryTest {
	static final String contentString = "This is a very small amount of content";
	
	protected static CCNLibrary library = null;
	
	static {
		library = StandardCCNLibrary.getLibrary();
	}

	@Test
	public void testPut() {
		Assert.assertNotNull(library);
		
		ContentName name = null;
		byte[] content = null;
//		ContentAuthenticator.ContentType type = ContentAuthenticator.ContentType.LEAF;
		PublisherID publisher = null;
		
		try {
			content = contentString.getBytes("UTF-8");	
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		try {
			name = new ContentName("/test/briggs/foo.txt");
		} catch (MalformedContentNameStringException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			CompleteName result = library.put(name, content, publisher);
			System.out.println("Resulting CompleteName: " + result);
		} catch (SignatureException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testRevision() {
		String key = "/test/key";
		byte[] data1 = "data".getBytes();
		byte[] data2 = "newdata".getBytes();
		CompleteName revision1;
		CompleteName revision2;
		
		try {
			ContentName keyName = new ContentName(key);
			revision1 = library.newVersion(keyName, data1);
			revision2 = library.newVersion(keyName, data2);
			int version1 = library.getVersionNumber(revision1.name());
			int version2 = library.getVersionNumber(revision2.name());
			System.out.println("Version1: " + version1 + " version2: " + version2);
			Assert.assertTrue("Revisions are strange", 
					version2 > version1);
		} catch (Exception e) {
			System.out.println("Exception in updating versions: " + e.getClass().getName() + ": " + e.getMessage());
			Assert.fail(e.getMessage());
		}	
	}
	
	@Test
	public void testRecall() {
		String key = "/test/smetters/values/data";
		byte[] data1 = "data".getBytes();
		try {
			ContentName keyName = new ContentName(key);
			CompleteName name = library.put(keyName, data1);
			System.out.println("Put under name: " + name.name());
			ArrayList<ContentObject> results = 
				library.get(
					name.name(), name.authenticator(), false);
			
			System.out.println("Querying for returned name, Got back: " + results.size() + " results.");
			
			if (results.size() == 0) {
				System.out.println("Didn't get back content we just put in.");
				System.out.println("Put under name: " + keyName);
				System.out.println("Final name: " + name.name());
				//Assert.fail("Didn't get back content we just put!");
			
				results = 
					library.get(
						name.name(), name.authenticator(), true);
				
				System.out.println("Recursive querying for returned name, Got back: " + results.size() + " results.");

				ContentName parentName = name.name().parent();
				System.out.println("Inserted name's parent same as key name? " + parentName.equals(keyName));
			
				ArrayList<CompleteName> parentChildren =
					CCNRepositoryManager.getRepositoryManager().getChildren(new CompleteName(parentName, null));
				
				System.out.println("GetChildren got " + parentChildren.size() + " children of expected parent.");
				boolean isInThere = false;
				for (int i=0; i < parentChildren.size(); ++i) {
					if (parentChildren.get(i).name().equals(name.name())) {
						System.out.println("Got a matching name. Authenticators match? " + name.authenticator().equals(parentChildren.get(i).authenticator()));
						isInThere = true;
						break;
					}
				}

				parentChildren =
					library.enumerate(new Interest(parentName));
				
				System.out.println("Enumerate got " + parentChildren.size() + " children of expected parent.");
				isInThere = false;
				for (int i=0; i < parentChildren.size(); ++i) {
					if (parentChildren.get(i).name().equals(name.name())) {
						System.out.println("Got a matching name. Authenticators match? " + name.authenticator().equals(parentChildren.get(i).authenticator()));
						isInThere = true;
						break;
					}
				}
				if (isInThere)
					System.out.println("Found expected node among children of parent.");
				else
					System.out.println("Didn't find expected node among children of parent.");
				
				ArrayList<CompleteName> enumerateResults = 
					library.enumerate(new Interest(name.name()));
				System.out.println("Got " + enumerateResults.size() + " matches for the name we just inserted.");
				isInThere = false;
				for (int i=0; i < enumerateResults.size(); ++i) {
					if (enumerateResults.get(i).name().equals(name.name())) {
						System.out.println("Got a matching name. Authenticators match? " + name.authenticator().equals(enumerateResults.get(i).authenticator()));
						isInThere = true;
						break;
					}
				}
				if (isInThere)
					System.out.println("Found expected node among enumeration of name.");
				else
					System.out.println("Didn't find expected node among enumeration of name.");
				
				
			} else {
				byte [] content = results.get(0).content();
				Assert.assertNotNull("No content associated with name we just put!", content);
				Assert.assertTrue("didn't get back same data", new String(data1).equals(new String(content)));
			}
			
			results = 
				library.get(
					keyName, null, true);
			
			System.out.println("Querying for inserted name, Got back: " + results.size() + " results.");
			
			if (results.size() == 0)
				Assert.fail("Didn't get back content we just put!");
			
			Iterator<ContentObject> rit = results.iterator();
			boolean gotit = false;
			while (rit.hasNext()) {
				ContentObject co = rit.next();
				if (co.name().equals(name.name()) &&
					co.authenticator().equals(name.authenticator())) {
					System.out.println("Got back name we inserted.");
					gotit = true;
					break;
				}
			}
			Assert.assertTrue("Didn't get back data we just inserted!",gotit);
		} catch (Exception e) {
			System.out.println("Exception in testing recall: " + e.getClass().getName() + ": " + e.getMessage());
			Assert.fail(e.getMessage());
		}
	}
	
	@Test
	public void testNotFound() throws Exception {
		try {
			String key = "/some_strange_key_we_should_never_find";
			ArrayList<ContentObject> results = 
				library.get(
					new ContentName(key), null, false);
			Assert.assertTrue("found something when there shouldn't have been anything", results.size() == 0);
		} catch (Exception e) {
			System.out.println("Exception in testing recall: " + e.getClass().getName() + ": " + e.getMessage());
			Assert.fail(e.getMessage());
		}
	}
	
	class TestListener extends BasicQueryListener {
		int _count = 0;
		Thread _mainThread;
		
		public TestListener(CCNBase queryProvider,
							Thread mainThread) {
			super(queryProvider);
			_mainThread = mainThread;
		}

		@Override
		public int handleResults(ArrayList<CompleteName> results) {
			byte[] content = null;
			try {
				if (null != results) {
					Iterator<CompleteName> rit = results.iterator();
					while (rit.hasNext()) {
						CompleteName name = rit.next();
						
						ArrayList<ContentObject> contents =
							library.get(name.name(), name.authenticator(), false);
					
						if (contents.size() > 1) {
							System.out.println("handleResults: notified about name: " + name.name() + " corresponds to " + contents.size() + " pieces of content.");
						}
						
						content = contents.get(0).content();
						String strContent = new String(content);
						System.out.println("Got update for " + contents.get(0).name() + ": " + strContent + " (revision " + library.getVersionNumber(contents.get(0).name()) + ")");
						_count++;
						switch(_count) {
						case 1:
							Assert.assertEquals("data1", strContent);
							System.out.println("Got data1 back!");
							_mainThread.interrupt();
							break;
						case 2: 
							Assert.assertEquals("data2", strContent);
							System.out.println("Got data2 back!");
							_mainThread.interrupt();
							break;
						default:
							Assert.fail("Somehow got a third update");
						}
					}
				}
			} catch (IOException e) {
				System.out.println("Exception in testing interests: " + e.getClass().getName() + ": " + e.getMessage());
				Assert.fail(e.getMessage());
			}
			return 0;
		}
	}

	@Test
	public void testInterest() {
		String key = "/test/interest";
		final Thread mainThread = Thread.currentThread();
		
		byte[] data1 = "data1".getBytes();
		byte[] data2 = "data2".getBytes();
		
		try {
			CCNQueryDescriptor qd =
				library.expressInterest(new Interest(key), 
									new TestListener(library, mainThread));
			
			library.put(new ContentName(key), data1);
			// wait a little bit before we move on...
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}

			library.put(new ContentName(key), data2);
			
			// wait a little bit before we move on...
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			
			library.cancelInterest(qd);
			
		} catch (Exception e) {
			System.out.println("Exception in testing interests: " + e.getClass().getName() + ": " + e.getMessage());
			Assert.fail(e.getMessage());
		}
	}
	
}
