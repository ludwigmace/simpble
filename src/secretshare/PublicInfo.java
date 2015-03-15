package secretshare;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Holds all the "publicly available" information about a secret share.
 * Holds both "required" and "optional" information.
 *
 */
public class PublicInfo
{
    // the required public info: "K" and the modulus
    final int k;                         // determines the order of the polynomial
    final BigInteger primeModulus;       // can be null

    // required for split: "N" - how many shares were generated?
    // optional for combine (can be null)
    final Integer n;

    // just descriptive info:
    private final String description;            // any string, including null
    private final String uuid;                   // a "Random" UUID string
    private final String date;                   // yyyy-MM-dd HH:mm:ss string

    public PublicInfo(final Integer inN,
                      final int inK,
                      final BigInteger inPrimeModulus,
                      final String inDescription)
    {
        super();
        this.n = inN;
        this.k = inK;
        this.primeModulus = inPrimeModulus;
        this.description = inDescription;

        UUID uuidobj = UUID.randomUUID();
        uuid =  uuidobj.toString();

        date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        if (n != null)
        {
            if (k > n)
            {
                throw new SecretShareException("k cannot be bigger than n [k=" + k +
                                               " n=" + n + "]");
            }
        }
    }

    @Override
    public String toString()
    {
        return "PublicInfo[k=" + k + ", n=" + n + "\n" +
            "modulus=" + primeModulus + "\n" +
            "description=" + description + "\n" +
            "date=" + date + "\n" +
            "uuid=" + uuid +
            "]";
    }
    public String debugDump()
    {
        return toString();
    }
    public final int getNforSplit()
    {
        if (n == null)
        {
            throw new SecretShareException("n was not set, can not perform split");
        }
        else
        {
            return n;
        }
    }
    public final int getN()
    {
        if (n == null)
        {
            return -1;
        }
        else
        {
            return n;
        }
    }
    public final int getK()
    {
        return k;
    }
    public final BigInteger getPrimeModulus()
    {
        return primeModulus;
    }
    public final String getDescription()
    {
        return description;
    }
    public final String getUuid()
    {
        return uuid;
    }
    public final String getDate()
    {
        return date;
    }
}