package com.parc.ccn.security.crypto.certificates;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

/**
 * @author D.K. Smetters
 *
 * Collection of crypto utility functions specific to bouncy castle.
 */
public class CryptoUtil {
    
	public static byte [] encode(DEREncodable encodable) throws CertificateEncodingException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DEROutputStream dos = new DEROutputStream(baos);
			dos.writeObject(encodable);
			dos.close();
		} catch (IOException ex) {
			throw new CertificateEncodingException("Cannot encode: " + ex.toString());
		}
		return baos.toByteArray();
	}		
	
	public static DERObject decode(byte [] decodable) throws CertificateEncodingException {
		DERObject dobj = null;
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(decodable);
			ASN1InputStream dis = new ASN1InputStream(bais);
			dobj = dis.readObject();
			dis.close();
		} catch (IOException ex) {
			throw new CertificateEncodingException("Cannot encode: " + ex.toString());
		}
		return dobj;
	}	

	public static PublicKey getPublicKey(SubjectPublicKeyInfo spki) 
				throws CertificateEncodingException, NoSuchAlgorithmException, 
								InvalidKeySpecException {
		// Reencode SubjectPublicKeyInfo, let java decode it.
		byte [] encodedKey = encode(spki);

		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
		String algorithmOID= 
				spki.getAlgorithmId().getObjectId().getId();
		String algorithm = OIDLookup.getCipherName(algorithmOID);
		if (algorithm == null) {
			throw new CertificateEncodingException("Unknown key algorithm!");
		}
		KeyFactory fact = KeyFactory.getInstance(algorithm);
		return fact.generatePublic(keySpec);
	}
	
	public static PublicKey getPublicKey(byte [] derEncodedPublicKey) throws 
			InvalidKeySpecException, 
			CertificateEncodingException, NoSuchAlgorithmException {
		
		// Problem is, we need the algorithm identifier inside
		// the key to decode it. So in essence we need to
		// decode it twice.
		DERObject genericObject = decode(derEncodedPublicKey);
		if (!(genericObject instanceof ASN1Sequence)) {
			throw new InvalidKeySpecException("This object is not a public key!");
		}
		
		// At this point it might also be a certificate, or
		// any number of things. 
		SubjectPublicKeyInfo spki = 
			new SubjectPublicKeyInfo((ASN1Sequence)genericObject);
		
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(derEncodedPublicKey);
		String algorithmOID= 
			spki.getAlgorithmId().getObjectId().getId();
		String algorithm = OIDLookup.getCipherName(algorithmOID);
		if (algorithm == null) {
			throw new NoSuchAlgorithmException("Unknown key algorithm: " + algorithmOID);
		}
		KeyFactory fact = KeyFactory.getInstance(algorithm);
		return fact.generatePublic(keySpec);
	}
	
	public static X509Certificate getCertificate(byte [] encodedCert) throws CertificateException {
		// Will make default provider's certificate if it has one.
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		return (X509Certificate)cf.generateCertificate(
			new ByteArrayInputStream(encodedCert));
	}
	
	private CryptoUtil() {
		super();
	}

}
