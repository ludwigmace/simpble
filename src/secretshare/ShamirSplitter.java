package secretshare;

/*******************************************************************************
 * Copyright (c) 2009, 2014 Tim Tiemens.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 *
 * Contributors:
 *     Tim Tiemens - initial API and implementation
 *******************************************************************************/

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

import android.util.Log;
import android.util.SparseArray;

/**
 * Main command line for the "split" (aka "create") of a secret.
 *
 * Takes a number of shares (n) and a threshold (k)
 *  and a secret (s) and creates the SecretShare.
 *
 * @author tiemens
 *
 */
public final class ShamirSplitter
{

	private static final String TAG = "shamir";
	static int paramK;
	static int paramN;
	static String paramS;

	static ByteArrayOutputStream os;
	static PrintStream ps;
	
    public static SparseArray<String> Workin(int k, int n, String s) {
    	
    	paramK = k;
    	paramN = n;
    	paramS = s;
    	
    	SplitInput input = SplitInput.buildSplitInput();
    	SplitOutput output = input.output();
        
    	SparseArray<String> sharesOut  = new SparseArray<String>();
    	
        for (ShareInfo i: output.getShares()) {
        	int x = i.getIndex();
        	BigInteger bi = i.getShare();
        	
        	sharesOut.put(x, bi.toString());
        }
        
        return sharesOut;

    }

    public static BigInteger parseBigInteger(String argname, String[] args, int index) {
        checkIndex(argname, args, index);

        String value = args[index];
        BigInteger ret = null;
        if (BigIntUtilities.Checksum.couldCreateFromStringMd5CheckSum(value))
        {
            try
            {
                ret = BigIntUtilities.Checksum.createBigInteger(value);
            }
            catch (SecretShareException e)
            {
                String m = "Failed to parse 'bigintcs:' because: " + e.getMessage();
                throw new SecretShareException(m, e);
            }
        }
        else
        {
            try
            {
                ret = new BigInteger(value);
            }
            catch (NumberFormatException e)
            {
                String m = "Failed to parse integer because: " + e.getMessage();
                throw new SecretShareException(m, e);
            }
        }

        return ret;
    }

    public static Integer parseInt(String argname, String[] args, int index) {
        checkIndex(argname, args, index);
        String value = args[index];

        Integer ret = null;
        try {
            ret = Integer.valueOf(value);
        }
        catch (NumberFormatException e) {
            String m = "The argument of '" + value + "' " + "is not a number.";
            throw new SecretShareException(m, e);
        }
        return ret;
    }


    public static void checkIndex(String argname, String[] args, int index) {
        if (index >= args.length)
        {
            throw new SecretShareException("The arg '-" + argname + "' requires an add'l arg");
        }
    }

    public static class SplitInput
    {
        // ==================================================
        // instance data
        // ==================================================

        // required arguments:
        private Integer k           = null;
        private Integer n           = null;
        private BigInteger secret   = null;

        // optional: if 'secret' was given as a human-string, this is non-null
        // else this is null
        private String secretArgument = null;

        // optional:  if null, then do not use modulus
        // default to 384-bit
        //private BigInteger modulus = SecretShare.getPrimeUsedFor384bitSecretPayload();
        private BigInteger modulus = null;

        // optional:
        //    paranoid: null = do nothing, paranoid < 0 = do all, otherwise paranoid = # of tests
        private Integer paranoid;

        // optional description
        private String description = null;

        // optional: the random can be seeded
        private Random random;

        // if true, print on 1 sheet of paper; otherwise use 'n' sheets and repeat the header
        private boolean printAllSharesAtOnce = true;

        // if true, print the original equation
        private boolean debugPrintEquationCoefficients = false;

        // ==================================================
        // constructors
        // ==================================================
        public static SplitInput buildSplitInput()
        {
            SplitInput ret = new SplitInput();
            
            ret.k = paramK;
            ret.n = paramN;
            ret.secretArgument = paramS;
            ret.secret = BigIntUtilities.Human.createBigInteger(paramS);
            ret.modulus = SecretShare.getPrimeUsedFor4096bigSecretPayload();
            

            if (ret.modulus != null) {
                if (! SecretShare.isTheModulusAppropriateForSecret(ret.modulus, ret.secret))
                {
                    final String originalString;
                    if (ret.secretArgument != null)
                    {
                        originalString = "[" + ret.secretArgument + "]";
                    }
                    else
                    {
                        originalString = "";
                    }

                    final String sInfo;
                    String sAsString = "" + ret.secret;
                    if (sAsString.length() < 25)
                    {
                        sInfo = sAsString;
                    }
                    else
                    {
                        sInfo = "length is " + sAsString.length() + " digits";
                    }
                    String m = "The secret " + originalString +  " (" + sInfo + ") is too big.  " +
                            "Please adjust the prime modulus or use -primeNone";

                    throw new SecretShareException(m);

                }
            }

            if (ret.random == null)
            {
                ret.random = new SecureRandom();
            }
            return ret;
        }

        // ==================================================
        // public methods
        // ==================================================
        public SplitOutput output()
        {
            SplitOutput splitOutput = new SplitOutput(this);
            
            splitOutput.setPrintAllSharesAtOnce(printAllSharesAtOnce);

            PublicInfo publicInfo = new PublicInfo(this.n, this.k, this.modulus, this.description);

            SecretShare secretShare = new SecretShare(publicInfo);

            SplitSecretOutput generate = secretShare.split(this.secret, this.random);

            splitOutput.splitSecretOutput = generate;

            return splitOutput;
        }


        // ==================================================
        // non public methods
        // ==================================================
    }

    public static class SplitOutput
    {
        private static final String SPACES = "                                              ";
        private boolean printAllSharesAtOnce = true;

        private final SplitInput splitInput;
        private SplitSecretOutput splitSecretOutput;
        private ParanoidOutput paranoidOutput = null; // can be null

        public SplitOutput(SplitInput inSplitInput)
        {
            this(true, inSplitInput);
        }

        public SplitOutput(boolean inPrintAllSharesAtOnce, SplitInput inSplitInput)
        {
            printAllSharesAtOnce = inPrintAllSharesAtOnce;
            splitInput = inSplitInput;
        }

        public void setPrintAllSharesAtOnce(boolean val)
        {
            printAllSharesAtOnce = val;
        }

        public void print(PrintStream out)
        {
            if (printAllSharesAtOnce)
            {
                printPolynomialEquation(out);
                printHeaderInfo(out);
                printSharesAllAtOnce(out);
            }
            else
            {
                printPolynomialEquation(out);
                printSharesOnePerPage(out);
            }
        }

        private void printPolynomialEquation(PrintStream out)
        {
            if (splitInput.debugPrintEquationCoefficients)
            {
                splitSecretOutput.debugPrintEquationCoefficients(out);
            }
        }

  

        private void printSharesOnePerPage(PrintStream out)
        {
            final List<ShareInfo> shares = splitSecretOutput.getShareInfos();
            boolean first = true;
            for (ShareInfo share : shares)
            {
                if (! first)
                {
                    printSeparatePage(out);
                }
                first = false;

                printHeaderInfo(out);

                printShare(out, share, false);
                printShare(out, share, true);

            }

        }

        private void printSeparatePage(PrintStream out)
        {
            out.print("\u000C");
        }

        private void printHeaderInfo(PrintStream out)
        {
            final PublicInfo publicInfo = splitSecretOutput.getPublicInfo();

            field(out, "Secret Share version ", "");
            field(out, "Date", publicInfo.getDate());
            field(out, "UUID", publicInfo.getUuid());
            field(out, "Description", publicInfo.getDescription());

            markedValue(out, "n", publicInfo.getN());
            markedValue(out, "k", publicInfo.getK());
            markedValue(out, "modulus", publicInfo.getPrimeModulus(), false);
            markedValue(out, "modulus", publicInfo.getPrimeModulus(), true);
        }

        public List<ShareInfo> getShares() {
        	 return splitSecretOutput.getShareInfos();
        }
        
        private void printSharesAllAtOnce(PrintStream out)
        {
            List<ShareInfo> shares = splitSecretOutput.getShareInfos();
            out.println("");
            for (ShareInfo share : shares)
            {
                printShare(out, share, false);
            }
            for (ShareInfo share : shares)
            {
                printShare(out, share, true);
            }
        }
        private void markedValue(PrintStream out, String fieldname, BigInteger number, boolean printAsBigIntCs)
        {
            String s;
            if (number != null)
            {
                if (printAsBigIntCs)
                {
                    s = BigIntUtilities.Checksum.createMd5CheckSumString(number);
                }
                else
                {
                    s = number.toString();
                }
                out.println(fieldname + " = " + s);
            }
            else
            {
                // no modulus supplied, do nothing
            }
        }
        
        private void markedValue(PrintStream out, String fieldname, int n)
        {
            out.println(fieldname + " = " + n);
        }


        private void field(PrintStream out, String label, String value)
        {
            if (value != null)
            {
                String sep;
                String pad;
                if ((label.length() > 0) &&
                    (! label.trim().equals("")))
                {
                    pad = label + SPACES;
                    pad = pad.substring(0, 30);
                    if (value.equals(""))
                    {
                        pad = label;
                        sep = "";
                    }
                    else
                    {
                        sep = ": ";
                    }
                }
                else
                {
                    pad = label;
                    sep = "";
                }

                out.println(pad + sep + value);
            }
        }

        private void printShare(PrintStream out, ShareInfo share, boolean printAsBigIntCs)
        {
            markedValue(out, "Share (x:" + share.getIndex() + ")", share.getShare(), printAsBigIntCs);
        }
    }

}
