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

import java.io.PrintStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

// import com.tiemens.secretshare.exceptions.SecretShareException;
//import com.tiemens.secretshare.math.BigIntStringChecksum;
//import com.tiemens.secretshare.math.CombinationGenerator;
//import com.tiemens.secretshare.math.EasyLinearEquation;
//import com.tiemens.secretshare.math.PolyEquationImpl;

/**
 * Main class for the "Shamir's Secret Sharing" implementation.
 *
 * General description:
 *   A secret is divided into "n" pieces of data,
 *   such that "k" of those pieces can be used to reconstruct the secret,
 *   but "k-1" of those pieces gets you nothing.
 *
 * The polynomials generated will be order "k-1",
 *   e.g. if k=3, then f(x) = secret + a*x^1 + b*x^2
 *    and there will be "k" of those polynomials,
 *    each with random [and discarded] coefficients 'a', 'b', etc.
 *
 * @author tiemens
 *
 */
public class SecretShare
{
    // ==================================================
    // class static data
    // ==================================================

    // ==================================================
    // class static methods
    // ==================================================

    /**
     * http://www.cromwell-intl.com/security/crypto/diffie-hellman.html says
     * "... choosing some prime p which is larger than the largest possible secret key".
     *
     * Sadly, "larger" is not enough for this implementation [for an unknown reason].
     *
     * Some test data shows:
     *  for a secret with 103 bits, 115 is not enough    116 is enough
     *  for a secret with 159 bits,                      160 is enough
     *
     * So - this method provides guidance on the modulus to use
     *      for a given secret.
     *
     * @param secret number
     * @return a modulus that (should) work in this library.
     *         It may be larger than it needs to be, but it will work.
     */
    public static BigInteger createAppropriateModulusForSecret(BigInteger secret)
    {
        final BigInteger ret;
        final int originalBitLength = secret.bitLength();

        //
        // be conservative  192 bits ->  180 cutoff
        //                  384 bits ->  370 cutoff
        //                 4096 bits -> 4024 cutoff
        //

        if (originalBitLength < 180)
        {
            ret = getPrimeUsedFor192bitSecretPayload();
        }
        else if (originalBitLength < 370)
        {
            ret = getPrimeUsedFor384bitSecretPayload();
        }
        else if (originalBitLength < 4024)
        {
            ret = getPrimeUsedFor4096bigSecretPayload();
        }
        else
        {
            //
            // if you make it here, you are 4000+ bits big.
            // and this call is going to be really expensive
            //
            ret = createRandomModulusForSecret(secret);
        }
        return ret;
    }

    /**
     * NOTE: you should prefer createAppropriateModulusForSecret() over this method.
     *
     * @param secret as biginteger
     * @return prime modulus big enough for secret
     */
    public static BigInteger createRandomModulusForSecret(BigInteger secret)
    {
        Random random = new SecureRandom();

        return createRandomModulusForSecret(secret, random);
    }

    /**
     * NOTE: you should prefer createAppropriateModulusForSecret() over this method.
     *
     * @param secret as biginteger
     * @param random you provide the random
     * @return prime modulus big enough for secret*
     */
    public static BigInteger createRandomModulusForSecret(BigInteger secret,
                                                          Random random)
    {
        final BigInteger ret;
        final int originalBitLength = secret.bitLength();

        final int numberOfBitsBigger = originalBitLength / 5;

        final int numbits = originalBitLength + numberOfBitsBigger;
        //System.out.println("Secret.bits=" + originalBitLength + " modulus.bits=" + numbits);

        // This could take a really long time, especially for 4000+ bits....
        ret = BigInteger.probablePrime(numbits, random);

        return ret;
    }

    public static boolean isTheModulusAppropriateForSecret(BigInteger modulus,
                                                           BigInteger secret)
    {
        try
        {
            checkThatModulusIsAppropriate(modulus, secret);
            return true;
        }
        catch (SecretShareException e)
        {
            return false;
        }
    }

    public static void checkThatModulusIsAppropriate(BigInteger primeModulus,
                                                     BigInteger secret)
        throws SecretShareException
    {
        if (secret.compareTo(primeModulus) >= 0)
        {
            throw new SecretShareException("Secret cannot be larger than modulus.  " +
                                           "Secret=" + secret + "\n" +
                                           "Modulus=" + primeModulus);
        }

        // Question - look at other rules?

    }



    // All primes were tested via http://www.alpertron.com.ar/ECM.HTM
    // All primes were tested with 100,000 iterations of Miller-Rabin

    public static BigInteger getPrimeUsedFor4096bigSecretPayload()
    {
        // GENERATE:
        // This big integer was created with probablePrime(BigInteger.valueOf(2L).pow(4100)).nextProbablePrime()
        // It took 28 seconds to generate [Run on Core i7 920 2.67Ghz]

        // CHECKING APPLET:
        // It took 25 minutes to check using alpertron.com.ar/ECM.HTM applet. [Run on Core2Duo E8500 3.16GHz CPU]
        // It took 18 minutes to check using alpertron.com.ar/ECM.HTM applet. [Run on Corei7-2600k 3.4GHz CPU, only 1 core working]
        // The applet is labeled "Factorization using the Elliptic Curve Method"
        //     and says "Rabin probabilistic prime check routine" and "Base used" hits 17000+
        // Output:
        //    Factorization complete in 0d 0h 17m 35s
        //      ECM: 0 modular multiplications
        //      Prime checking: 8449024 modular multiplications
        //      Timings:   Primality test of 1 number: 0d 0h 17m 34.2s
        //
        // CHECKING COMMAND LINE: ("threads" set to 8)
        // It took 5 minutes [Run on Corei7-2600K 3.4GHz CPU, 4 cores working - config to x8 more, running x4 more, result x3 faster]
        //
        // OTHER INFO:
        // This number is 1,234 digits long
        BigInteger p4096one =
                new BigInteger(
            "1671022210261044010706804337146599012127" +
            "9427984758140486147735732543262527544919" +
            "3095812289909599609334542417074310282054" +
            "0780117501097269771621177740562184444713" +
            "5311624699359973445785442150139493030849" +
            "1201896951396220211014303634039307573549" +
            "4951338587994892653929285926514054477984" +
            "1897745831487644537568464106991023630108" +
            "6045751504900830441750495932712549251755" +
            "0884842714308894440025555839788342744866" +
            "7101368958164663781091806630951947745404" +
            "9899622319436016030246615841346729868014" +
            "9869334160881652755341231281231973786191" +
            "0590928243420749213395009469338508019541" +
            "0958855418900088036159728065975165578015" +
            "3079187511387238090409461192977321170936" +
            "6081401737953645348323163171237010704282" +
            "8481068031277612787461827099245660019965" +
            "4423851454616735972464821439378482870833" +
            "7709298145449348366148476664877596527269" +
            "1765522730435723049823184958030880339674" +
            "1433100452606317504985611860713079871716" +
            "8809146278034477061142090096734446658190" +
            "8273334857030516871663995504285034522155" +
            "7158160427604895839673593745279150722839" +
            "3997083495197879290548002853265127569910" +
            "9306488129210915495451479419727501586051" +
            "1232507931203905482587057398637416125459" +
            "0876872367709717423642369650017374448020" +
            "8386154750356267714638641781056467325078" +
            "08534977443900875333446450467047221"
            );

        // No, these "0"s are not an error.
        //  The nextProbablePrime is only "735(hex)" away from 2^4100...
        // In case you are wondering, the "bigintcs" encoding is a guard
        //  against an accidental change in the string.
        //   (Big Integer Checksum)
        String bigintcs =
            "bigintcs:100000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000000-000000-000000-" +
            "000000-000000-000000-000000-000735-4C590B";

        // Compare the values of both strings before returning a value.
        // This guards against accidental changes to the strings
        return checkAndReturn("4096bit prime", p4096one, bigintcs);
    }


    public static BigInteger getPrimeUsedFor384bitSecretPayload()
    {
        // This big integer was created with probablePrime(386-bits)
        // This prime is bigger than 2^384
        BigInteger p194one =
            new BigInteger("830856716641269388050926147210" +
                           "378437007763661599988974204336" +
                           "741171904442622602400099072063" +
                           "84693584652377753448639527");

        String bigintcs =
            "bigintcs:000002-1bd189-52959f-874f79-3d6cf5-11ac82-e6cea4-46c19c-5f523a-5318c7-" +
            "e0f379-66f9e1-308c61-2d8d0b-dba253-6f54b0-ec6c27-3198DB";

        return checkAndReturn("384bit prime", p194one, bigintcs);
    }

    public static BigInteger getPrimeUsedFor192bitSecretPayload()
    {
        // This big integer was created with probablePrime(194-bits)
        // This prime is bigger than 2^192
        BigInteger p194one =
            new BigInteger("14976407493557531125525728362448106789840013430353915016137");

        String bigintcs =
            "bigintcs:000002-62c8fd-6ec81b-3c0584-136789-80ad34-9269af-da237f-8ff3c9-12BCCD";

        return checkAndReturn("192bit prime", p194one, bigintcs);
    }


    /**
     * Guard against accidental changes to the strings.
     *
     * @param which caller
     * @param expected value as biginteger
     * @param asbigintcs value as big-integer-checksum string
     * @return expected or throw exception
     * @throws SecretShareException if expected does not match asbigintcs
     */
    private static BigInteger checkAndReturn(String which,
                                             BigInteger expected,
                                             String asbigintcs)
    {
        BigInteger other =
                BigIntStringChecksum.fromString(asbigintcs).asBigInteger();

        if (expected.equals(other))
        {
            return expected;
        }
        else
        {
            throw new SecretShareException(which + " failure");
        }
    }
    // ==================================================
    // instance data
    // ==================================================
    private final PublicInfo publicInfo;

    // ==================================================
    // factories
    // ==================================================

    // ==================================================
    // constructors
    // ==================================================

    public SecretShare(final PublicInfo inPublicInfo)
    {
        publicInfo = inPublicInfo;
    }


    // ==================================================
    // public methods
    // ==================================================

    /**
     * Split the secret into pieces.
     *
     * @param secret to split
     * @return split secret output instance
     */
    public SplitSecretOutput split(final BigInteger secret)
    {
        return split(secret, new SecureRandom());
    }

    /**
     * Split the secret into pieces, where the caller controls the random instance.
     *
     * @param secret to split
     * @param random to use for random number generation
     * @return split secret output instance
     */
    public SplitSecretOutput split(final BigInteger secret, final Random random)
    {
        if (secret == null) {
            throw new SecretShareException("Secret cannot be null");
        }
        
        if (secret.signum() <= 0) {
            throw new SecretShareException("Secret cannot be negative");
        }
        
        if (publicInfo.getPrimeModulus() != null) {
            checkThatModulusIsAppropriate(publicInfo.getPrimeModulus(), secret);
        }

        BigInteger[] coeffs = new BigInteger[publicInfo.getK()];

        // create the equation by setting the coefficients:
        // [a] randomize the coefficients:
        randomizeCoeffs(coeffs, random, publicInfo.getPrimeModulus(), secret);
        // [b] set the constant coefficient to the secret:
        coeffs[0] = secret;

        final PolyEquationImpl equation = new PolyEquationImpl(coeffs);

        SplitSecretOutput ret = new SplitSecretOutput(this.publicInfo, equation);

        for (int x = 1, n = publicInfo.getNforSplit() + 1; x < n; x++) {
            final BigInteger fofx = equation.calculateFofX(BigInteger.valueOf(x));
            BigInteger data = fofx;
            
            if (publicInfo.primeModulus != null) {
                data = data.mod(publicInfo.primeModulus);
            }
            
            final ShareInfo share = new ShareInfo(x, data, this.publicInfo);
            ret.sharesInfo.add(share);
        }

        return ret;
    }

    /**
     * Combine the shares generated by the split to recover the secret.
     *
     * @param usetheseshares shares to use
     * @return the combine output instance [which in turn contains the recovered secret]
     */
    public CombineOutput combine(final List<ShareInfo> usetheseshares)
    {
        CombineOutput ret = null;

        sanityCheckPublicInfos(publicInfo, usetheseshares);

        if (publicInfo.getK() > usetheseshares.size())
        {
            throw new SecretShareException("Must have " + publicInfo.getK() + " shares to solve.  Only provided " + usetheseshares.size());
        }

        checkForDuplicatesOrThrow(usetheseshares);


        final int size = publicInfo.getK();
        BigInteger[] xarray = new BigInteger[size];
        BigInteger[] fofxarray = new BigInteger[size];
        for (int i = 0, n = size; i < n; i++)
        {
            xarray[i] = usetheseshares.get(i).getXasBigInteger();
            fofxarray[i] = usetheseshares.get(i).getShare();
        }
        EasyLinearEquation ele =
            EasyLinearEquation.createForPolynomial(xarray, fofxarray);
        if (publicInfo.getPrimeModulus() != null)
        {
            ele = ele.createWithPrimeModulus(publicInfo.getPrimeModulus());
        }
        EasyLinearEquation.EasySolve solve = ele.solve();

        BigInteger solveSecret = solve.getAnswer(1);
        if (publicInfo.getPrimeModulus() != null)
        {
            solveSecret = solveSecret.mod(publicInfo.getPrimeModulus());
        }
        ret = new CombineOutput(solveSecret);


        return ret;
    }

    /**
     * @param outer - usually the one from SecretShare.publicInfo
     * @param list  - share info list that also have publicInfos
     * @throws SecretShareException if something does not match
     */
    private void sanityCheckPublicInfos(PublicInfo      outer,
                                        List<ShareInfo> list)
    {
        // Basic sanity checks:
        if (outer == null)
        {
            throw new SecretShareException("Public Info [outer] cannot be null");
        }
        if (outer.k <= 0)
        {
            throw new SecretShareException("Public Info [outer] k must be positive, k=" + outer.k);
        }
        // Note: there is no restriction on outer.primeModulus -- it is allowed to be null

        if (list == null)
        {
            throw new SecretShareException("Public Info [list] cannot be null");
        }

        // First way you are ok: if every list[x].getPublicInfo() == null

        // any list[x] == null?
        boolean anyShareNullsInList = false;
        // ALL list[x].publicInfo() == null?
        boolean allNullInList       = true;
        // ANY list[x].publicInfo() == null?
        boolean anyNullInList       = false;

        for (ShareInfo share : list)
        {
            if (share != null)
            {
                if (share.getPublicInfo() != null)
                {
                    allNullInList = false;
                }
                else
                {
                    anyNullInList = true;
                }
            }
            else
            {
                anyShareNullsInList = true;
            }
        }

        if (allNullInList)
        {
            return;
        }
        // For now, if there are SOME nulls in the list or SOME null-publicInfos in list, then OK
        if (anyShareNullsInList)
        {
            // could throw an exception here, but for now, don't
        }
        if (anyNullInList)
        {
            // could throw an exception here, but for now, don't
        }


        // Next -- make sure all 'n' and 'k' match
        for (int i = 0, n = list.size(); i < n; i++)
        {
            final ShareInfo share = list.get(i);
            // See above: we only check if the share is not null
            if (share != null)
            {
                sanityCheckShareInfo(outer, i, share);
            }
        }
    }

    /**
     *
     * @param outer  - usually the one from SecretShare.publicInfo
     * @param index - if not null, used to document location in list
     * @param share - the shareInfo to check
     * @throws SecretShareException if something does not match
     */
    private void sanityCheckShareInfo(final PublicInfo outer,
                                      Integer index,
                                      final ShareInfo share)
    {
        String indexInfo = index == null ? "" : "[" + index + " ] ";
        if (outer.k != share.getPublicInfo().k)
        {
            throw new SecretShareException("Public Info " + indexInfo + "mismatch on k, should be = "+
                                           outer.k + " but was = " + share.getPublicInfo().k);
        }

        // N is allowed to be null in 'outer' - make sure it matches
        if (! matches(outer.n, share.getPublicInfo().n))
        {
            throw new SecretShareException("Public Info " + indexInfo + "mismatch on n, should be = "+
                    outer.n + " but was = " + share.getPublicInfo().n);
        }

        // primeModulus is allowed to be null in 'outer' - make sure it matches
        if (! matches(outer.primeModulus, share.getPublicInfo().primeModulus))
        {
            throw new SecretShareException("Public Info " + indexInfo + "mismatch on modulus, should be = "+
                    outer.primeModulus + " but was = " + share.getPublicInfo().primeModulus);
        }
    }

    private boolean matches(Object a, Object b)
    {
        if (a == null)
        {
            return b == null;
        }
        else
        {
            return a.equals(b);
        }
    }
    private void checkForDuplicatesOrThrow(List<ShareInfo> shares)
    {
        Set<ShareInfo> seen = new HashSet<ShareInfo>();
        for (ShareInfo s : shares)
        {
            if (seen.contains(s))
            {
                throw new SecretShareException("Duplicate share of " + s.debugDump());
            }
            else
            {
                seen.add(s);
            }
        }

    }

    // ==================================================
    // private methods
    // ==================================================

    private void randomizeCoeffs(final BigInteger[] coeffs,
                                 final Random random,
                                 final BigInteger modulus,
                                 final BigInteger secret)
    {
        for (int i = 1, n = coeffs.length; i < n; i++)
        {
            BigInteger big = null;
            //big = BigInteger.valueOf((random.nextInt() % 20) + 1);

            big = BigInteger.valueOf(random.nextLong());
            // ENHANCEMENT: provide better control?  make it even bigger?
            // for now, we'll just do long^2:
            big = big.multiply(BigInteger.valueOf(random.nextLong()));

            // FIX? TODO:? FIX?
            big = big.abs(); // make it positive

            coeffs[i] = big;

            // Book says "all coefficients are smaller than the modulus"
            if (modulus != null)
            {
                coeffs[i] = coeffs[i].mod(modulus);
            }

            // FIX? TODO: FIX? experiment says "all coefficients are smaller than the secret"
            coeffs[i] = coeffs[i].mod(secret);
        }
    }


    public ParanoidOutput combineParanoid(List<ShareInfo> shares)
    {
        return combineParanoid(shares, null);
    }

    public ParanoidOutput combineParanoid(List<ShareInfo> shares,
                                          Integer maximumCombinationsToTest)
    {
        ParanoidOutput ret = new ParanoidOutput();
        ret.maximumCombinationsAllowedToTest = maximumCombinationsToTest;

        BigInteger answer = null;

        CombinationGenerator<ShareInfo> combo =
            new CombinationGenerator<ShareInfo>(shares,
                                                publicInfo.getK());
        ret.totalNumberOfCombinations = combo.getTotalNumberOfCombinations();

        final int percentEvery = 30;  // or 10 for every 10%
        int outputEvery = 100;
        if (maximumCombinationsToTest != null)
        {
            if (BigInteger.valueOf(maximumCombinationsToTest)
                    .compareTo(combo.getTotalNumberOfCombinations()) > 0)
            {
                maximumCombinationsToTest = combo.getTotalNumberOfCombinations().intValue();
                outputEvery = (maximumCombinationsToTest * percentEvery ) / 100 + 1;
            }
        }
        else
        {
            outputEvery = (combo.getTotalNumberOfCombinations().intValue() * percentEvery ) / 100  + 1;
        }


        int count = -1;
        for (List<ShareInfo> usetheseshares : combo)
        {
            count++;
            if (maximumCombinationsToTest != null)
            {
                if (count > maximumCombinationsToTest)
                {
                    break;
                }
            }

            if ((count % outputEvery) == 0)
            {
                ret.recordCombination(combo.getCurrentCombinationNumber(),
                                      combo.getIndexesAsString(),
                                      dumpshares(usetheseshares));
            }

            SecretShare.CombineOutput solved = this.combine(usetheseshares);
            BigInteger solve =  solved.getSecret();
            if (answer == null)
            {
                answer = solve;
            }
            else
            {
                if (! answer.equals(solve))
                {
                    throw new SecretShareException("Paranoid combine failed, on combination at count=" + count);
                }
            }
        }
        ret.agreedAnswerEveryTime = answer;

        return ret;
    }

    /**
     * Holds the output of the combine() operation, i.e. the original secret.
     *
     */
    public static class CombineOutput
    {
        private final BigInteger secret;

        public CombineOutput(final BigInteger inSecret)
        {
            secret = inSecret;
        }

        public final BigInteger getSecret()
        {
            return secret;
        }
    }


    // ==================================================
    // non public methods
    // ==================================================

    private String dumpshares(List<ShareInfo> usetheseshares)
    {
        String ret = "";
        for (ShareInfo share : usetheseshares)
        {
            ret += " " + share.getShare();
        }
        return ret;
    }


}
