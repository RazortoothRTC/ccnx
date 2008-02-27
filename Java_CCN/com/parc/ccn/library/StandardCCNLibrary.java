package com.parc.ccn.library;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.SignatureException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;

import javax.xml.stream.XMLStreamException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.parc.ccn.Library;
import com.parc.ccn.config.ConfigurationException;
import com.parc.ccn.data.CompleteName;
import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.ContentObject;
import com.parc.ccn.data.content.Collection;
import com.parc.ccn.data.content.Header;
import com.parc.ccn.data.content.Link;
import com.parc.ccn.data.query.CCNQueryDescriptor;
import com.parc.ccn.data.query.CCNQueryListener;
import com.parc.ccn.data.query.Interest;
import com.parc.ccn.data.security.ContentAuthenticator;
import com.parc.ccn.data.security.KeyLocator;
import com.parc.ccn.data.security.LinkAuthenticator;
import com.parc.ccn.data.security.PublisherID;
import com.parc.ccn.data.security.ContentAuthenticator.ContentType;
import com.parc.ccn.network.CCNInterestManager;
import com.parc.ccn.network.CCNRepositoryManager;
import com.parc.ccn.security.crypto.CCNMerkleTree;
import com.parc.ccn.security.crypto.DigestHelper;
import com.parc.ccn.security.keys.KeyManager;

/**
 * A basic implementation of the CCNLibrary API. This
 * rides on top of the CCNBase low-level interface. It uses
 * CCNRepositoryManager to interface with a "real" virtual CCN,
 * and KeyManager to interface with the user's collection of
 * signing and verification keys. 
 * 
 * Need to expand get-side interface to allow querier better
 * access to signing information and trust path building.
 * 
 * @author smetters
 *
 */
public class StandardCCNLibrary implements CCNLibrary {

	public static final String MARKER = "_";
	public static final String VERSION_MARKER = MARKER + "v" + MARKER;
	public static final String FRAGMENT_MARKER = MARKER + "b" + MARKER;
	public static final String CLIENT_METADATA_MARKER = MARKER + "meta" + MARKER;
	public static final String HEADER_NAME = ".header";
	
	static {
		Security.addProvider(new BouncyCastleProvider());
	}
	
	protected static StandardCCNLibrary _library = null;

	/**
	 * Do we want to do this this way, or everything static?
	 */
	protected KeyManager _userKeyManager = null;
	
	public static StandardCCNLibrary getLibrary() { 
		if (null == _library) {
			synchronized (StandardCCNLibrary.class) {
				if (null == _library) {
					try {
						_library = new StandardCCNLibrary();
					} catch (ConfigurationException e) {
						Library.logger().severe("Configuration exception initializing CCN library: " + e.getMessage());
						throw new RuntimeException("Configuration exception initializing CCN library: " + e.getMessage(), e);
					}
				}
			}
		}
		return _library;
	}

	public StandardCCNLibrary(KeyManager keyManager) {
		_userKeyManager = keyManager;
	}

	public StandardCCNLibrary() throws ConfigurationException {
		this(KeyManager.getDefaultKeyManager());
	}

	public void setKeyManager(KeyManager keyManager) {
		if (null == keyManager) {
			Library.logger().warning("StandardCCNLibrary::setKeyManager: Key manager cannot be null!");
			throw new IllegalArgumentException("Key manager cannot be null!");
		}
		_userKeyManager = keyManager;
	}

	public KeyManager keyManager() { return _userKeyManager; }

	public PublisherID getDefaultPublisher() {
		return keyManager().getDefaultKeyID();
	}

	public CompleteName addCollection(ContentName name, Link [] contents) throws SignatureException, IOException {
		return addCollection(name, contents, getDefaultPublisher());
	}

	public CompleteName addCollection(
			ContentName name, 
			Link [] contents,
			PublisherID publisher) throws SignatureException, IOException {
		try {
			return addCollection(name, contents, publisher, null, null);
		} catch (InvalidKeyException e) {
			Library.logger().warning("Default key invalid.");
			Library.warningStackTrace(e);
			throw new SignatureException(e);
		} catch (NoSuchAlgorithmException e) {
			Library.logger().warning("Default key has invalid algorithm.");
			Library.warningStackTrace(e);
			throw new SignatureException(e);
		}
	}

	public CompleteName addCollection(
			ContentName name, 
			Link[] contents,
			PublisherID publisher, KeyLocator locator,
			PrivateKey signingKey) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, IOException {
		
		Collection collectionData = new Collection(contents);

		if (null == signingKey)
			signingKey = keyManager().getDefaultSigningKey();

		if (null == locator)
			locator = keyManager().getKeyLocator(signingKey);
	
		try {
			return put(name, collectionData.canonicalizeAndEncode(signingKey), ContentType.CONTAINER, publisher);
		} catch (XMLStreamException e) {
			Library.logger().warning("Cannot canonicalize a standard container!");
			Library.warningStackTrace(e);
			throw new IOException("Cannot canonicalize a standard container!");
		}
	}

	public CompleteName addToCollection(
			ContentName name,
			CompleteName[] additionalContents) {
		// TODO Auto-generated method stub
		return null;
	}

	public CompleteName removeFromCollection(
			ContentName name,
			CompleteName[] additionalContents) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Links are signed by the publisher of the link. However,
	 * the content of the link is an XML document that contains
	 * a complete name, including an indication of who the linker
	 * trusts to write the linked document (or to extend the
	 * linked-to hierarchy). The type of key referred to in the
	 * linked-to name is any of the usual types (key, cert, or
	 * name), but it can play one of two roles -- SIGNER, or
	 * the direct signer of the content, or CERTIFIER, the
	 * person who must have certified whoever's key signed
	 * the linked-to content. 
	 * @param destAuthenticator can be null
	 * @throws SignatureException 
	 * @throws IOException 
	 */
	public CompleteName link(ContentName src, ContentName target,
			LinkAuthenticator targetAuthenticator) throws SignatureException, IOException {
		return link(src, target, targetAuthenticator, getDefaultPublisher());
	}

	public CompleteName link(ContentName src, ContentName target,
			LinkAuthenticator targetAuthenticator,
			PublisherID publisher) throws SignatureException, IOException {
		try {
			return link(src,target,targetAuthenticator,publisher,null,null);
		} catch (InvalidKeyException e) {
			Library.logger().warning("Default key invalid.");
			Library.warningStackTrace(e);
			throw new SignatureException(e);
		} catch (NoSuchAlgorithmException e) {
			Library.logger().warning("Default key has invalid algorithm.");
			Library.warningStackTrace(e);
			throw new SignatureException(e);
		}
	}

	/**
	 * TODO: better answer than throwing an exception on invalid
	 * args.
	 * @throws NoSuchAlgorithmException 
	 * @throws SignatureException 
	 * @throws InvalidKeyException 
	 * @throws IOException 
	 * @throws XMLStreamException 
	 */
	public CompleteName link(ContentName src, ContentName target,
			LinkAuthenticator targetAuthenticator, 
			PublisherID publisher, KeyLocator locator,
			PrivateKey signingKey) throws InvalidKeyException, SignatureException, 
						NoSuchAlgorithmException, IOException {

		if ((null == src) || (null == target)) {
			Library.logger().info("Link: src and target cannot be null.");
			throw new IllegalArgumentException("Link: src and target cannot be null.");
		}
		
		if (null == signingKey)
			signingKey = keyManager().getDefaultSigningKey();

		if (null == locator)
			locator = keyManager().getKeyLocator(signingKey);
	
		Link linkData = new Link(target, targetAuthenticator);
		try {
			return put(src, linkData.canonicalizeAndEncode(signingKey), ContentType.LINK, publisher, locator, signingKey);
		} catch (XMLStreamException e) {
			Library.logger().warning("Cannot canonicalize a standard link!");
			Library.warningStackTrace(e);
			throw new IOException("Cannot canonicalize a standard link! " + e.getMessage());
		}
	}

	public ContentObject getLink(CompleteName name) {
		if (!isLink(name))
			return null;
		// Want the low-level get.
		return null;
	}

	public boolean isLink(CompleteName name) {
		return (name.authenticator().type() == ContentType.LINK);
	}

	/**
	 * Just attempt to generate the latest version of a piece
	 * of content, regardless of who authored the previous
	 * version.
	 * 
	 * Right now have all sorts of uncertainties in versioning --
	 * do we know the latest version number of a piece of content?
	 * Even if we've read it, it isn't atomic -- by the time
	 * we write our new version, someone else might have updated
	 * the number...
	 */
	public CompleteName newVersion(ContentName name,
								   byte[] contents) throws SignatureException, IOException {
		return newVersion(name, contents, getDefaultPublisher());
	}

	/**
	 * @param publisher Who we want to publish this as,
	 * not who published the existing version.
	 */
	public CompleteName newVersion(
			ContentName name, 
			byte[] contents,
			PublisherID publisher) throws SignatureException, IOException {

		try {
			ContentName latestVersion = 
				getLatestVersionName(name, null);
		
			int currentVersion = -1;
			if (null != latestVersion)
				// will return -1 if unversioned 
				currentVersion = getVersionNumber(latestVersion);
			
			// This ends us up with version numbers starting
			// at 0. If we want version numbers starting at 1,
			// modify this.
			return addVersion(name, currentVersion+1, contents, publisher, null, null);
		
		} catch (InvalidKeyException e) {
			Library.logger().info("InvalidKeyException using default key.");
			throw new SignatureException(e);
		} catch (SignatureException e) {
			Library.logger().info("SignatureException using default key.");
			throw e;
		} catch (NoSuchAlgorithmException e) {
			Library.logger().info("NoSuchAlgorithmException using default key.");
			throw new SignatureException(e);
		}
	}

	public CompleteName addVersion(ContentName name, int version, byte [] contents,
			PublisherID publisher, KeyLocator locator,
			PrivateKey signingKey) throws SignatureException, 
			InvalidKeyException, NoSuchAlgorithmException, IOException {

		if (null == signingKey)
			signingKey = keyManager().getDefaultSigningKey();

		if (null == locator)
			locator = keyManager().getKeyLocator(signingKey);
		
		if (null == publisher) {
			publisher = keyManager().getPublisherID(signingKey);
		}
		
		// Construct new name
		// <name>/<VERSION_MARKER>/<version_number>
		ContentName versionedName = versionName(name, version);

		// put result
		return put(versionedName, contents, ContentAuthenticator.ContentType.LEAF, publisher, locator, signingKey);
	}

	/**
	 * Because getting just the latest version number would
	 * require getting the latest version name first, 
	 * just get the latest version name and allow caller
	 * to pull number.
	 * @return If null, no existing version found.
	 */
	public ContentName getLatestVersionName(ContentName name, PublisherID publisher) {
		// Challenge -- Dan's proposed latest version syntax,
		// <name>/latestversion/1/2/3... works well if there
		// are 12 versions, not if there are a million. 
		// Need to do a limited get/enumerate just to get version
		// names, without enumerating all the blocks.
		// DKS TODO general way of doing this
		// right now use list children. Should be able to do
		// it in Jackrabbit with XPath.
		ContentName baseVersionName = 
			new ContentName(versionRoot(name), VERSION_MARKER);
		try {
			// Because we're just looking at children of
			// the name -- not actual pieces of content --
			// look only at ContentNames.
			ArrayList<CompleteName> availableVersions = 
				CCNRepositoryManager.getRepositoryManager().getChildren(new CompleteName(baseVersionName, null));
			
			if ((null == availableVersions) || (availableVersions.size() == 0)) {
				// No existing version.
				return null;
			}
			
			Iterator<CompleteName> vit = availableVersions.iterator();
			
			// Assume (not currently true) that we've gotten
			// back only content that matches our (publisher) criteria.
			// So just need to sort on version numbers.
			int latestVersion = -1;
			ContentName latestVersionName = null;
			while (vit.hasNext()) {
				CompleteName version = vit.next();
				int thisVersion =
					getVersionNumber(version.name());
				if (thisVersion > latestVersion) {
					latestVersion = thisVersion;
					latestVersionName = version.name();
				}
			}
			// Should we rely on unique names? Or worry
			// about CompleteNames? We only really have 
			// ContentNames here, so return just that.
			return latestVersionName;
			
		} catch (IOException e) {
			Library.logger().warning("IOException getting latest version number of name: " + name + ": " + e.getMessage());
			Library.warningStackTrace(e);
		}
		return null;
	}

	/**
	 * Extract the version information from this name.
	 * @return Version number, or -1 if not versioned.
	 */
	public int getVersionNumber(ContentName name) {
		int offset = name.containsWhere(VERSION_MARKER);
		if (offset < 0)
			return -1; // no version information.
		return Integer.valueOf(ContentName.componentPrint(name.component(offset+1)));
	}

	/**
	 * @param name
	 * @param version
	 * @return
	 */
	public ContentName versionName(ContentName name, int version) {
		ContentName baseName = name;
		if (isVersioned(name))
			baseName = versionRoot(name);
		return new ContentName(baseName, 
							   ContentName.componentParse(VERSION_MARKER),
							   ContentName.componentParse(Integer.toString(version)));
	}
	
	public static boolean isVersioned(ContentName name) {
		return name.contains(VERSION_MARKER);
	}

	public static ContentName versionRoot(ContentName name) {
		return name.cut(VERSION_MARKER);
	}
	
	public CompleteName put(ContentName name, byte[] contents) 
				throws SignatureException, IOException {
		return put(name, contents, getDefaultPublisher());
	}

	public CompleteName put(ContentName name, byte[] contents, 
			PublisherID publisher) throws SignatureException, IOException {
		return put(name, contents, ContentAuthenticator.ContentType.LEAF, publisher);
	}

	public CompleteName put(ContentName name, byte[] contents, 
			ContentAuthenticator.ContentType type,
			PublisherID publisher) throws SignatureException, IOException {
		try {
			return put(name, contents, type, publisher, 
					   null, null);
		} catch (InvalidKeyException e) {
			Library.logger().info("InvalidKeyException using default key.");
			throw new SignatureException(e);
		} catch (SignatureException e) {
			Library.logger().info("SignatureException using default key.");
			throw e;
		} catch (NoSuchAlgorithmException e) {
			Library.logger().info("NoSuchAlgorithmException using default key.");
			throw new SignatureException(e);
		}
	}

	/**
	 * If small enough, doesn't fragment. Otherwise, does.
	 * Return CompleteName of the thing they put (in the case
	 * of a fragmented thing, the header). That way the
	 * caller can then also easily link to that thing if
	 * it needs to, or put again with a different name.
	 * 
	 * We want to generate a unique name (just considering
	 * the name part) for transport and routing layer efficiency. 
	 * We want to do this in a way that
	 * gives us the following properties:
	 * <ol>
	 * <li>General CCN nodes do not need to understand any
	 *   name components.
	 * <li>General CCN nodes can verify content signatures if
	 * 	 they feel so inclined. That means that any components
	 *   added to the name to make it unique must be signed
	 *   along with the rest of the name.
	 * <li>General CCN nodes need to know as few algorithms
	 *   for verifying content signatures as possible; at
	 *   minimum one for leaf content and one for fragmented
	 *   content (probably also one for streamed content).
	 * <li>If a particular CCN node wishes to interpret the
	 * 	 content of the additional component (or query over it),
	 * 	 they can, but we don't require them to be able to.
	 * <li>Making content names unique shouldn't interfere with
	 * 	 making names or content private. Content can be encrypted
	 *   before hashing; name components could be encrypted even
	 *   after uniquification (so no one can tell that two blocks
	 *   have the same content, or even anything about the block
	 *   that maps to a name).
	 * </ol>
	 * Requiring the result to be unique means that the additional
	 * component added can't simply be the content digest, or
	 * the publisher ID. Either of these could be useful, but
	 * neither is guaranteed to be unique. The signature is guaranteed
	 * to be unique, but including the signature in the name itself
	 * (or the digest of the signature, etc) means that the name
	 * cannot be completely signed -- as the signature can't be
	 * included in the name for signing. At least the user-intended
	 * part of the name must signed, and including the signature
	 * in a distinguished component of the name means that CCN
	 * nodes must understand what parts of the name are signed
	 * and what aren't. While this is necessarily true, e.g. for
	 * fragmented data (see below), you either need a way to
	 * verify the remainder of the name (which is possible for
	 * fragmented data), or only require users to sign name prefixes.
	 * It is much better to require verification of the entire
	 * name, either by signing it completely (for unfragmented data),
	 * or by including the fragment names in the block information
	 * incorporated in the hash tree for signing (see below).
	 * So, what we use for unfragmented data is the digest of 
	 * the content authenticator without the signature in it; 
	 * which in turn contains the digest
	 * of the content itself, as well as the publisher ID and
	 * the timestamp (which will make it unique). When we generate
	 * the signature, we still sign the name, the content authenticator,
	 * and the content, as we cannot guarantee that the content
	 * authenticator digest has been incorporated in the name.
	 * 
	 * For fragmented data, we only generate one signature,
	 * on the root of the Merkle hash tree. For that we use
	 * this same process to generate a unique name from the
	 * content name and content information. However, we then
	 * decorate that name to create the individual block names;
	 * rather than have CCN nodes understand how to separate
	 * that decoration and verify it, we incorporate the block
	 * names into the Merkle hash tree.
	 * 
	 * TODO: DKS: improve this to handle stream writes better.
	 * What happens if we want to write a block at a time.
	 * @throws IOException 
	 **/
	public CompleteName put(ContentName name, byte [] contents,
			ContentAuthenticator.ContentType type,
			PublisherID publisher, KeyLocator locator,
			PrivateKey signingKey) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, IOException {
	
		if (null == signingKey)
			signingKey = keyManager().getDefaultSigningKey();

		if (null == locator)
			locator = keyManager().getKeyLocator(signingKey);
		
		if (null == publisher) {
			publisher = keyManager().getPublisherID(signingKey);
		}
	
		Library.logger().info("Putting content: " + name.toString());
		if (contents.length >= Header.DEFAULT_BLOCKSIZE) {
			return fragmentedPut(name, contents, type, publisher, locator, signingKey);
		} else {
			// We need to generate unique name, and 
			// generate signed ContentAuthenticator.
			CompleteName uniqueName =
				ContentAuthenticator.generateAuthenticatedName(
						name, publisher, ContentAuthenticator.now(),
						type, contents, false,
						locator, signingKey);
			try {
				Library.logger().info("Final put name: " + uniqueName.name().toString());
				return put(uniqueName.name(), uniqueName.authenticator(), contents);
			} catch (IOException e) {
				Library.logger().warning("This should not happen: put failed with an IOExceptoin.");
				Library.warningStackTrace(e);
				throw e;
			}
		}
	}

	/** 
	 * Low-level fragmentation interface.
	 * @param name
	 * @param contents
	 * @param type
	 * @param publisher
	 * @param locator
	 * @param signingKey
	 * @throws InvalidKeyException
	 * @throws SignatureException
	 * @throws NoSuchAlgorithmException
	 * @throws IOException 
	 */
	protected CompleteName fragmentedPut(ContentName name, byte [] contents,
			ContentAuthenticator.ContentType type,
			PublisherID publisher, KeyLocator locator,
			PrivateKey signingKey) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, IOException {
		// This will call into CCNBase after picking appropriate credentials
		// take content, blocksize (static), divide content into array of 
		// content blocks, call hash fn for each block, call fn to build merkle
		// hash tree.   Build header, for each block, get authinfo for block,
		// (with hash tree, block identifier, timestamp -- SQLDateTime)
		// insert header using mid-level insert, low-level insert for actual blocks.
		// We should implement a non-fragmenting put.   Won't do block stuff, will need to do latest version stuff.
		int blockSize = Header.DEFAULT_BLOCKSIZE;
		int nBlocks = (contents.length + blockSize - 1) / blockSize;
		int from = 0;
		byte[][] contentBlocks = new byte[nBlocks][];
		Timestamp timestamp = new Timestamp(System.currentTimeMillis());
		for (int i = 0; i < nBlocks; i++) {
			int to = from + blockSize;
			// nice Arrays operations not in 1.5
			int end = (to < contents.length) ? to : contents.length;
//			contentBlocks[i] = Arrays.copyOfRange(contents, from, (to < contents.length) ? to : contents.length);
			contentBlocks[i] = new byte[end-from];
			System.arraycopy(contents, from, contentBlocks[i], 0, (to < contents.length) ? to : contents.length);
		}

		// Digest of complete contents
		// If we're going to unique-ify the block names
		// (or just in general) we need to incorporate the names
		// in the MerkleTree blocks. 
    	CCNMerkleTree tree = 
    		new CCNMerkleTree(name, publisher, timestamp, 
    						  contentBlocks, locator, signingKey);

		for (int i = 0; i < nBlocks; i++) {
			try {
				CompleteName blockCompleteName = tree.getBlockCompleteName(i);
				put(blockCompleteName.name(), blockCompleteName.authenticator(), contentBlocks[i]);
			} catch (IOException e) {
				Library.logger().warning("This should not happen: we cannot put our own blocks!");
				Library.warningStackTrace(e);
				throw e;
			}
		}
		// construct the headerBlockContents;
		byte [] contentDigest = DigestHelper.digest(contents);
		Header header = new Header(contents.length, contentDigest, tree.root());
		byte[] encodedHeader = null;
		try {
			encodedHeader = header.encode();
		} catch (XMLStreamException e) {
			Library.logger().warning("This should not happen: we cannot encode our own header!");
			Library.warningStackTrace(e);
			throw new IOException("This should not happen: we cannot encode our own header!" + e.getMessage());
		}

		// Add another differentiator to avoid making header
		// name prefix of other valid names?
		ContentName headerName = headerName(name);
		CompleteName headerBlockInformation =
			ContentAuthenticator.generateAuthenticatedName(
					headerName, publisher, timestamp, type, 
					encodedHeader, false, locator, signingKey);
		try {
			put (headerBlockInformation.name(), headerBlockInformation.authenticator(), encodedHeader);
		} catch (IOException e) {
			Library.logger().warning("This should not happen: we cannot put our own header!");
			Library.warningStackTrace(e);
			throw e;
		}
		return headerBlockInformation;
	}
	
	/**
	 * Might want to make headerName not prefix of  rest of
	 * name, but instead different subleaf. 
	 * the header name of v.6 of name <name>
	 * was originally <name>/_v_/6; could be 
	 * <name>/_v_/6/.header or <name>/_v_/6/_m_/.header;
	 * the full uniqueified names would be:
	 * <name>/_v_/6/<sha256> or <name>/_v_/6/.header/<sha256>
	 * or <name>/_v_/6/_m_/.header/<sha256>.
	 * The first version has the problem that the
	 * header name (without the unknown uniqueifier)
	 * is the prefix of the block names; so relies on the
	 * packet scheduler to get us a header rather than a block.
	 * Given a block we can't even derive the uniqueified
	 * name of the header... 
	 * So, even though we need to cope with on-path data
	 * in general, for this most critical case make our lives
	 * easier by making the header block namable without
	 * getting all the data blocks as well by recursion.
	 * @param name
	 * @return
	 */
	public static ContentName headerName(ContentName name) {
		// Want to make sure we don't add a header name
		// to a fragment. Go back up to the fragment root.
		if (isFragment(name))
			return new ContentName(fragmentRoot(name), HEADER_NAME);
		return new ContentName(name, HEADER_NAME);
	}
	
	public static ContentName headerRoot(ContentName headerName) {
		// Do we want to handle fragment roots, etc, here too?
		if (!isHeader(headerName)) {
			throw new IllegalArgumentException("Name " + headerName.toString() + " is not a header name.");
		}
		// Strip off any header-specific prefix info if we
		// add any.
		return headerName.cut(HEADER_NAME); 
	}
	
	public static boolean isHeader(ContentName name) {
		// with on-path header, no way to tell except
		// that it wasn't a fragment. With separate name,
		// easier to handle.
		return (name.contains(HEADER_NAME));
	}

	public static boolean isFragment(ContentName name) {
		return name.contains(FRAGMENT_MARKER);
	}
	
	public static ContentName fragmentRoot(ContentName name) {
		return name.cut(FRAGMENT_MARKER);
	}
	
	public static ContentName fragmentName(ContentName name, int i) {
		return new ContentName(name, 
							ContentName.componentParse(FRAGMENT_MARKER),
							ContentName.componentParse(Integer.toString(i)));
	}
	
	/**
	 * Extract the fragment information from this name.
	 */
	public static int getFragment(ContentName name) {
		int offset = name.containsWhere(FRAGMENT_MARKER);
		return Integer.valueOf(ContentName.componentPrint(name.component(offset+1)));
	}
	
	/**
	 * Implementation of CCNBase. Pass on to repository
	 * manager.
	 */

	/**
	 * Implementation of CCNBase.put.
	 */
	public CompleteName put(ContentName name, 
							ContentAuthenticator authenticator,
							byte[] content) throws IOException {
		return CCNRepositoryManager.getRepositoryManager().put(name, authenticator, content);
	}

	/**
	 * The low-level get just gets us blocks that match this
	 * name. (Have to think about metadata matches.) 
	 * Trying to map this into a higher-order "get" that
	 * unfragments and reads into a single buffer is challenging.
	 * For now, let's just pass this one through to the bottom
	 * level, and use open and read to defragment.
	 * 
	 * DKS TODO: should this get at least verify?
	 */
	public ArrayList<ContentObject> get(ContentName name, 
										ContentAuthenticator authenticator,
										boolean isRecursive) throws IOException {
		return CCNRepositoryManager.getRepositoryManager().get(name, authenticator,isRecursive);
	}

	/**
	 * The rest of CCNBase. Pass it on to the CCNInterestManager to
	 * forward to the network. Also express it to the
	 * repositories we manage, particularly the primary.
	 * Each might generate their own CCNQueryDescriptor,
	 * so we need to group them together.
	 */
	public CCNQueryDescriptor expressInterest(
			Interest interest,
			CCNQueryListener listener) throws IOException {
		
		// Express interest to the network and to the repositories.
		// TODO DKS amalgamate across queries to return single query descriptor that
		// can be used to cancel.
		CCNInterestManager.getInterestManager().expressInterest(interest, listener);
		return CCNRepositoryManager.getRepositoryManager().expressInterest(interest, listener);
	}

	public void cancelInterest(CCNQueryDescriptor query) throws IOException {
		CCNInterestManager.getInterestManager().cancelInterest(query);
		CCNRepositoryManager.getRepositoryManager().cancelInterest(query);
	}
	
	/**
	 * Enumerate matches in the local repositories.
	 * TODO: maybe filter out fragments, possibly other metadata.
	 * TODO: add in communication layer to talk just to
	 * local repositories for v 2.0 protocol.
	 * @param query At this point, always recursive.
	 * @return
	 * @throws IOException 
	 */
	public ArrayList<CompleteName> enumerate(Interest query) throws IOException {
		return CCNRepositoryManager.getRepositoryManager().enumerate(query);		
	}

	/**
	 * Do need a way to enumerate just the high-level documents,
	 * prior to fragmentation. Particularly necessary to cope
	 * with version collision errors...
	 */
	public ArrayList<CompleteName> enumerateDocuments(Interest query) throws IOException {
		ContentName nameToOpen = query.name();
		if (isFragment(query.name())) {
			// DKS TODO: should we do this?
			nameToOpen = fragmentRoot(nameToOpen);
		}
		if (!isVersioned(nameToOpen)) {
			// if publisherID is null, will get any publisher
			nameToOpen = 
				getLatestVersionName(nameToOpen, 
									 query.publisherID());
		}
		
		// Should have name of root of version we want to
		// open. Get the header block. Already stripped to
		// root. We've altered the header semantics, so that
		// we can just get headers rather than a plethora of
		// fragments. 
		ContentName headerName = headerName(nameToOpen);
		// This might not be unique - 
		// we could have here either multiple versions of
		// a given number, or multiple of a given number
		// by a given publisher. If the latter, pick by latest
		// after verifying. If the former, pick by latest
		// version crossed with trust.
		// DKS TODO figure out how to intermix trust information.
		// DKS TODO -- overloaded authenticator as query;
		// doesn't work well - would have to check that right things
		// are asked.
		// DKS TODO -- does get itself do a certain amount of
		// prefiltering? Can it mark objects as verified?
		// do we want to use the low-level get, as the high-level
		// one might unfragment?
		ArrayList<CompleteName> documents = 
						enumerate(new Interest(headerName, query.publisherID()));
		
		return documents;
	}
	
	/**
	 * High-level verify. Calls low-level verify, if we
	 * don't think this has been verified already. Probably
	 * need to separate to keep the two apart.
	 * @param object
	 * @param publicKey The key to use to verify the signature,
	 * 	or null if the key should be retrieved using the key 
	 *  locator.
	 * @return
	 * @throws XMLStreamException 
	 * @throws NoSuchAlgorithmException 
	 * @throws SignatureException 
	 * @throws InvalidKeyException 
	 */
	public boolean verify(ContentObject object, PublicKey publicKey) 
			throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, XMLStreamException {
		
		try {
			if (!object.verify(publicKey)) {
				Library.logger().warning("Low-level verify failed on " + object.name());
			}
		} catch (Exception e) {
			Library.logger().warning("Exception " + e.getClass().getName() + " during verify: " + e.getMessage());
			Library.warningStackTrace(e);
		}
		return true;
		// TODO DKS
		//throw new UnsupportedOperationException("Implement me!");
	}
	
	/**
	 * Open this name for reading (for now). If the name
	 * is versioned, open that version. Otherwise, open the
	 * latest version. If the name is a fragment, just open that one.
	 * Implicitly implements query match.
	 * Currently suggests can query match only on publisher...
	 * need to systematize this. Some of this might want
	 * to move into CCNDescriptor.
	 * 
	 * For now, it looks like the library-level (defragmenting)
	 * get will be implemented in terms of these operations,
	 * rather than the other way 'round. So these should use
	 * the low-level (CCNBase/CCNRepository/CCNNetwork) get.
	 * @throws IOException 
	 */
	public CCNDescriptor open(CompleteName name) throws IOException {
		ContentName nameToOpen = name.name();
		if (isFragment(name.name())) {
			// DKS TODO: should we do this?
			nameToOpen = fragmentRoot(nameToOpen);
		}
		if (!isVersioned(nameToOpen)) {
			// if publisherID is null, will get any publisher
			nameToOpen = 
				getLatestVersionName(nameToOpen, 
									 name.authenticator().publisherID());
		}
		
		// Should have name of root of version we want to
		// open. Get the header block. Already stripped to
		// root. We've altered the header semantics, so that
		// we can just get headers rather than a plethora of
		// fragments. 
		ContentName headerName = headerName(nameToOpen);
		// This might not be unique - 
		// we could have here either multiple versions of
		// a given number, or multiple of a given number
		// by a given publisher. If the latter, pick by latest
		// after verifying. If the former, pick by latest
		// version crossed with trust.
		// DKS TODO figure out how to intermix trust information.
		// DKS TODO -- overloaded authenticator as query;
		// doesn't work well - would have to check that right things
		// are asked.
		// DKS TODO -- does get itself do a certain amount of
		// prefiltering? Can it mark objects as verified?
		// do we want to use the low-level get, as the high-level
		// one might unfragment?
		ArrayList<ContentObject> headers = get(headerName, name.authenticator(),false);
		
		if ((null == headers) || (headers.size() == 0)) {
			Library.logger().info("No available content named: " + headerName.toString());
			throw new FileNotFoundException("No available content named: " + headerName.toString());
		}
		// So for each header, we assume we have a potential document.
		
		// First we verify. (Or should get have done this for us?)
		// We don't bother complaining unless we have more than one
		// header that matches. Given that we would complain for
		// that, we need an enumerate that operates at this level.)
		Iterator<ContentObject> headerIt = headers.iterator();
		while (headerIt.hasNext()) {
			ContentObject header = headerIt.next();
			// TODO: DKS: should this be header.verify()?
			// Need low-level verify as well as high-level verify...
			// Low-level verify just checks that signer actually signed.
			// High-level verify checks trust.
			try {
				if (!verify(header, null)) {
					Library.logger().warning("Found header: " + header.name().toString() + " that fails to verify.");
					headerIt.remove();
				}
			} catch (Exception e) {
				Library.logger().warning("Got an " + e.getClass().getName() + " exception attempting to verify header: " + header.name().toString() + ", treat as failure to verify.");
				Library.warningStackTrace(e);
				headerIt.remove();
			}
		}
		if (headers.size() == 0) {
			Library.logger().info("No available verifiable content named: " + headerName.toString());
			throw new FileNotFoundException("No available verifiable content named: " + headerName.toString());
		}
		if (headers.size() > 1) {
			Library.logger().info("Found " + headers.size() + " headers matching the name: " + headerName.toString());
			throw new IOException("CCNException: More than one (" + headers.size() + ") valid header found for name: " + headerName.toString() + " in open!");
		}
		if (headers.get(0) == null) {
			Library.logger().info("Found only null headers matching the name: " + headerName.toString());
			throw new IOException("CCNException: No non-null header found for name: " + headerName.toString() + " in open!");
		}
		
		try {
			return new CCNDescriptor(headers.get(0), true);
		} catch (XMLStreamException e) {
			Library.logger().warning("XMLStreamException: trying to create a CCNDescriptor from header: " + headerName.toString());
			Library.warningStackTrace(e);
			throw new IOException("XMLStreamException: trying to create a CCNDescriptor from header: " + headerName.toString());
		}
	}
		
	public long read(CCNDescriptor ccnObject, byte [] buf, long 
											offset, long len) {
		return ccnObject.read(buf,offset,len);
	}

	public int seek(CCNDescriptor ccnObject, long offset, CCNDescriptor.SeekWhence whence) {
		return ccnObject.seek(offset, whence);
	}
	
	public long tell(CCNDescriptor ccnObject) {
		return ccnObject.tell();
	}

	public boolean isLocal(CompleteName name) {
		// TODO Auto-generated method stub
		return false;
	}
}
