package secretshare;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import android.util.SparseArray;

/**
 * Main command line for the "combine" (aka "recover") of a secret.
 *
 * Takes a threshold (k), and a modulus [if any],
 *  and "k" secrets with their index,
 *  and recovers the original secret.
 *
 * @author tiemens
 *
 */
public final class ShamirCombiner {

	private static final String TAG = "shamir";
	static ByteArrayOutputStream os;
	static PrintStream ps;
	static SparseArray<String> shareHolders;
	
	static int paramK = 3;

    public static void Workin() {
    	os = new ByteArrayOutputStream();
    	ps = new PrintStream(os);
    	
    	shareHolders = new SparseArray<String>();
    	
    	/* 
    	 * 1 - 7352982178009263086080134442619255798685951
    	 * 2 - 7353048696234791595232193259853689164535904
    	 * 3 - 7353155358651940110092497654124346676348823
    	 * 4 - 7353302165260708630661047625431228334124708
    	 * 5 - 7353489116061097156937843173774334137863559
    	 * 6 - 7353716211053105688922884299153664087565376
    	 */
    	shareHolders.append(2, "7353048696234791595232193259853689164535904");
    	shareHolders.append(4, "7353302165260708630661047625431228334124708");
    	shareHolders.append(6, "7353716211053105688922884299153664087565376");
    	
    	CombineInput input = CombineInput.buildCombineInput();
        CombineOutput output = input.output();

        output.print(ps);
        String results = "";
        
        try {
			results = os.toString("UTF8");
		} catch (UnsupportedEncodingException e) {
			results = "error";
		}
        
        Log.v(TAG, results);


    }



    private static BigInteger parseBigInteger(String argname, String[] args, int index) {
        return ShamirSplitter.parseBigInteger(argname, args, index);
    }

    private static Integer parseInt(String argname, String[] args, int index) {
        return ShamirSplitter.parseInt(argname, args, index);
    }

    public static class CombineInput {

        // required arguments:
        private Integer k           = null;

        private final List<SecretShare.ShareInfo> shares = new ArrayList<SecretShare.ShareInfo>();

        private BigInteger modulus = SecretShare.getPrimeUsedFor384bitSecretPayload();

        // optional: for combine, we don't need n, but you can provide it
        private Integer n           = null;

        // not an input.  used to cache the PublicInfo, so that after the first ShareInfo is
        //  created with this PublicInfo, then they are all created with the same PublicInfo
        private PublicInfo publicInfo;

        // ==================================================
        // constructors
        // ==================================================
        public static CombineInput buildCombineInput() {
            CombineInput ret = new CombineInput();

        	ret.k = paramK;
        	ret.n = ret.k;
        	ret.modulus = SecretShare.getPrimeUsedFor384bitSecretPayload();

        	for (int i = 0; i < shareHolders.size(); i++) {
        		int sharenum = shareHolders.keyAt(i);
        		String shareval = shareHolders.get(sharenum);
        		
        		SecretShare.ShareInfo share = ret.parseEqualShare(sharenum, shareval);
        		
        		ret.addIfNotDuplicate(share);
        	}
        	

            if (ret.shares.size() < ret.k) {
                throw new SecretShareException("k set to " + ret.k + " but only " + ret.shares.size() + " shares provided");
            }

            return ret;
        }

        private void addIfNotDuplicate(ShareInfo add)
        {
            boolean shouldadd = true;
            for (ShareInfo share : this.shares)
            {
                if (share.getX() == add.getX())
                {
                    // dupe
                    if (! share.getShare().equals(add.getShare()))
                    {
                        throw new SecretShareException("share x:" + share.getX() +
                                                       " was entered with two different values " +
                                                       "(" + share.getShare() + ") and (" +
                                                       add.getShare() + ")");
                    }
                    else
                    {
                        shouldadd = false;
                    }
                }
                else if (share.getShare().equals(add.getShare()))
                {
                    throw new SecretShareException("duplicate share values at x:" +
                                                   share.getX() + " and x:" +
                                                   add.getX());
                }
            }
            if (shouldadd)
            {
                this.shares.add(add);
            }
        }


        /**
         *
         * @param fieldname description of source of data
         * @param line is "standard format for share", example:
         *   Share (x:2) = 481883688232565050752267350226995441999530323860
         * @return ShareInfo (integer and big integer)
         */
        private ShareInfo parseEqualShare(int sharenum, String shareval)
        {
            if (this.publicInfo == null)
            {
                this.publicInfo = constructPublicInfoFromFields("parseEqualShare");
            }

            // create a BigInteger from the string
            BigInteger shareValAsBigInt = new BigInteger(shareval);

            return new ShareInfo(sharenum, shareValAsBigInt, this.publicInfo);
        }

        private PublicInfo constructPublicInfoFromFields(String where)
        {
            return new SecretShare.PublicInfo(this.n, this.k, this.modulus, "MainCombine:" + where);
        }

        //  Share (x:2) = bigintcs:005468-69732d-4e02c5-7b11d2-9d4426-e26c88-8a6f94-9809A9
        private int parseXcolon(String line) {
            String i = after(line, ":");
            int end = i.indexOf(")");
            i = i.substring(0, end);

            return Integer.valueOf(i);
        }

        private String after(String line, String lookfor) {
            return line.substring(line.indexOf(lookfor) + 1).trim();
        }

        private Integer parseEqualInt(String fieldname, String line) {
            String s = after(line, "=");
            return Integer.valueOf(s);
        }

        private static void checkRequired(String argname, Object obj) {
            if (obj == null)
            {
                throw new SecretShareException("Argument '" + argname + "' is required.");
            }
        }

        // ==================================================
        // public methods
        // ==================================================
        public CombineOutput output() {
            CombineOutput ret = new CombineOutput();
            ret.combineInput = this;

            // it is a "copy" since it should be equal to this.publicInfo
            SecretShare.PublicInfo copyPublicInfo = constructPublicInfoFromFields("output");

            SecretShare secretShare = new SecretShare(copyPublicInfo);

            SecretShare.CombineOutput combine = secretShare.combine(shares);

            ret.secret = combine.getSecret();

            return ret;
        }

        // ==================================================
        // non public methods
        // ==================================================
    }

    public static class CombineOutput {
        private BigInteger secret;

        private SecretShare.CombineOutput combineOutput;
        private CombineInput combineInput;

        public void print(PrintStream out) {

            out.println("secret.number = '" + secret + "'");
            String s = BigIntUtilities.Human.createHumanString(secret);
            out.println("secret.string = '" + s + "'");

        }


    }


}
