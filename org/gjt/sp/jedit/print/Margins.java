
package org.gjt.sp.jedit.print;

import java.util.Locale;
import javax.print.attribute.Attribute;
import javax.print.attribute.DocAttribute;
import javax.print.attribute.PrintJobAttribute;
import javax.print.attribute.PrintRequestAttribute;
import static javax.print.attribute.Size2DSyntax.*;


/**
 * Custom printing attribute to represent page margins.
 */
public class Margins implements DocAttribute, PrintRequestAttribute, PrintJobAttribute
{

    // margins are stored in micromillimeters
    private float top;
    private float left;
    private float right;
    private float bottom;

    // need serial version since this is serialized
    private static final long serialVersionUID = 5343792322705104289L;

    /**
     * @param units One of INCH or MM.
     */
    public Margins( float top, float left, float right, float bottom )
    {
        if ( top < 0.0 || left < 0.0 || right < 0.0 || bottom < 0.0 )
        {

            // this shouldn't happen since the printer dialog margin text fields
            // only accept positive numbers
            throw new IllegalArgumentException( "Invalid margin." );
        }

        float u = new Integer( getUnits() ).floatValue();
        this.top = top * u;
        this.left = left * u;
        this.right = right * u;
        this.bottom = bottom * u;
    }


    // returns INCH or MM depending on Locale
    // note that while Canada is mostly metric, Canadian paper sizes
    // are essentially US ANSI sizes rounded to the nearest 5 mm
    private int getUnits()
    {
        String country = Locale.getDefault().getCountry();
        if ( "".equals( country ) || Locale.US.getCountry().equals( country ) || Locale.CANADA.getCountry().equals( country ) )
        {
            return INCH;
        }


        return MM;
    }


    /**
     * Get the margins as an array of 4 values in the order
     * top, left, right, bottom. The values returned are in the given units.
     * @param  units Unit conversion factor, either INCH or MM.
     *
     * @return margins as array of top, left, right, bottom in the specified units.
     *
     * @exception  IllegalArgumentException on invalid units.
     */
    public float[] getMargins( int units )
    {
        switch ( units )

        {
            case INCH:
            case MM:
                break;
            default:
                throw new IllegalArgumentException( "Invalid units." );
        }

        return new float[] {getTop( units ), getLeft( units ), getRight( units ), getBottom( units )};
    }


    public float getTop( int units )
    {
        return convertFromMicrometers( top, units );
    }


    public float getLeft( int units )
    {
        return convertFromMicrometers( left, units );
    }


    public float getRight( int units )
    {
        return convertFromMicrometers( right, units );
    }


    public float getBottom( int units )
    {
        return convertFromMicrometers( bottom, units );
    }


    public final Class<? extends Attribute> getCategory()
    {
        return Margins.class;
    }


    public final String getName()
    {
        return "margins";
    }


    private float convertFromMicrometers( float margin, int units )
    {
        return margin / new Integer( units ).floatValue();
    }

    public String toString()
    {
        return toString(INCH);   
    }

    public String toString( int units )
    {
        String uom = "";
        switch ( units )

        {
            case INCH:
                uom = "in";
                break;
            case MM:
                uom = "mm";
                break;
            default:
                throw new IllegalArgumentException( "Invalid units." );
        }


        float[] margins = getMargins( units );
        StringBuilder sb = new StringBuilder(128);
        sb.append( "Margins(" ).append( uom ).append( ")[top:" ).append( margins[0] ).append( ", left:" );
        sb.append( margins[1] ).append( ", right:" ).append( margins[2] ).append( ", bottom:" ).append( margins[3] ).append( ']' );
        return sb.toString();
    }


    public boolean equals( Object object )
    {
        boolean toReturn = false;
        if ( object instanceof Margins )
        {
            Margins margins = ( Margins )object;
            if ( top == margins.top && left == margins.left && bottom == margins.bottom && right == margins.right )
            {
                toReturn = true;
            }
        }


        return toReturn;
    }


    public int hashCode()
    {
        return new Float(top).intValue() + 37 * new Float(left).intValue() + 43 * new Float(right).intValue() + 47 * new Float(bottom).intValue();
    }
}
