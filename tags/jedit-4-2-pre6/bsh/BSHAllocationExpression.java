/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    * 
 *                                                                           *
 *  The Original Code is BeanShell. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/


package bsh;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

/**
	New object, new array, or inner class style allocation with body.
*/
class BSHAllocationExpression extends SimpleNode
{
    BSHAllocationExpression(int id) { super(id); }

    public Object eval( CallStack callstack, Interpreter interpreter) 
		throws EvalError
    {
        // type is either a class name or a primitive type
        SimpleNode type = (SimpleNode)jjtGetChild(0);

        // args is either constructor arguments or array dimensions
        SimpleNode args = (SimpleNode)jjtGetChild(1);

        if ( type instanceof BSHAmbiguousName )
        {
            BSHAmbiguousName name = (BSHAmbiguousName)type;

            if (args instanceof BSHArguments)
                return objectAllocation(name, (BSHArguments)args, 
					callstack, interpreter );
            else
                return objectArrayAllocation(name, (BSHArrayDimensions)args, 
					callstack, interpreter );
        }
        else
            return primitiveArrayAllocation((BSHPrimitiveType)type,
                (BSHArrayDimensions)args, callstack, interpreter );
    }

    private Object objectAllocation(
		BSHAmbiguousName nameNode, BSHArguments argumentsNode, 
		CallStack callstack, Interpreter interpreter 
	) 
		throws EvalError
    {
		NameSpace namespace = callstack.top();

        Object[] args = argumentsNode.getArguments( callstack, interpreter );
        if ( args == null)
            throw new EvalError( "Null args in new.", this, callstack );

		// Look for scripted class object
        Object obj = nameNode.toObject( 
			callstack, interpreter, false/* force class*/ );

		// Is it a scripted class object?
		if ( ClassNameSpace.isScriptedClass( obj ) )
		{
			ClassNameSpace cns = (ClassNameSpace)((This)obj).getNameSpace();
			return cns.constructClassInstance( 
				args, interpreter, callstack, this );
		}

		// Try regular class

        obj = nameNode.toObject( 
			callstack, interpreter, true/*force class*/ );

        Class type = null;
		if ( obj instanceof ClassIdentifier )
        	type = ((ClassIdentifier)obj).getTargetClass();
		else
			throw new EvalError( "Can't new: "+obj, this, callstack );

		// Is an inner class style object allocation
		boolean hasBody = jjtGetNumChildren() > 2;

		if ( hasBody ) {
        	BSHBlock body = (BSHBlock)jjtGetChild(2);
			return constructWithBody( 
				type, args, body, callstack, interpreter );
		} else
			return constructObject( type, args, callstack );
    }

	private Object constructObject( 
		Class type, Object[] args, CallStack callstack ) 
		throws EvalError
	{
        try {
            return Reflect.constructObject( type, args );
        } catch ( ReflectError e) {
            throw new EvalError(
				"Constructor error: " + e.getMessage(), this, callstack );
        } catch(InvocationTargetException e) {
			// No need to wrap this debug
			Interpreter.debug("The constructor threw an exception:\n\t" +
				e.getTargetException());
            throw new TargetError(
				"Object constructor", e.getTargetException(), 
				this, callstack, true);
        }
	}

	private Object constructWithBody( 
		Class type, Object[] args, BSHBlock body,
		CallStack callstack, Interpreter interpreter ) 
		throws EvalError
	{
		if ( ! type.isInterface() )
			throw new EvalError(
				"BeanShell cannot extend class types: "+ type, this, callstack);

		NameSpace namespace = callstack.top();
// Maybe we should swap in local namespace for the top?
// who is the caller?
		NameSpace local = new NameSpace(namespace, "anonymous block object");
		callstack.push(local);
		body.eval( callstack, interpreter, true );
		callstack.pop();
		try {
			return local.getThis(interpreter).getInterface( type );
		} catch ( UtilEvalError e ) {
			throw e.toEvalError( this, callstack );
		}
	}

    private Object objectArrayAllocation(
		BSHAmbiguousName nameNode, BSHArrayDimensions dimensionsNode, 
		CallStack callstack, Interpreter interpreter 
	) 
		throws EvalError
    {
		NameSpace namespace = callstack.top();
        Class type = nameNode.toClass( callstack, interpreter );
        if ( type == null )
            throw new EvalError( "Class " + nameNode.getName(namespace) 
				+ " not found.", this, callstack );

		return arrayAllocation( dimensionsNode, type, callstack, interpreter );
    }

    private Object primitiveArrayAllocation(
		BSHPrimitiveType typeNode, BSHArrayDimensions dimensionsNode, 
		CallStack callstack, Interpreter interpreter 
	) 
		throws EvalError
    {
        Class type = typeNode.getType();

		return arrayAllocation( dimensionsNode, type, callstack, interpreter );
    }

	private Object arrayAllocation( 
		BSHArrayDimensions dimensionsNode, Class type, 
		CallStack callstack, Interpreter interpreter )
		throws EvalError
	{
		/*
			dimensionsNode can return either a fully intialized array or VOID.
			when VOID the prescribed array dimensions (defined and undefined)
			are contained in the node.
		*/
        Object result = dimensionsNode.eval( type, callstack, interpreter );
        if ( result != Primitive.VOID )
            return result;
		else
			return arrayNewInstance( type, dimensionsNode, callstack );
	}

	/**
		Create an array of the dimensions specified in dimensionsNode.
		dimensionsNode may contain a number of "undefined" as well as "defined"
		dimensions.
		<p>

		Background: in Java arrays are implemented in arrays-of-arrays style
		where, for example, a two dimensional array is a an array of arrays of
		some base type.  Each dimension-type has a Java class type associated 
		with it... so if foo = new int[5][5] then the type of foo is 
		int [][] and the type of foo[0] is int[], etc.  Arrays may also be 
		specified with undefined trailing dimensions - meaning that the lower 
		order arrays are not allocated as objects. e.g.  
		if foo = new int [5][]; then foo[0] == null //true; and can later be 
		assigned with the appropriate type, e.g. foo[0] = new int[5];
		(See Learning Java, O'Reilly & Associates more background).
		<p>

		To create an array with undefined trailing dimensions using the
		reflection API we must use an array type to represent the lower order
		(undefined) dimensions as the "base" type for the array creation... 
		Java will then create the correct type by adding the dimensions of the 
		base type to specified allocated dimensions yielding an array of
		dimensionality base + specified with the base dimensons unallocated.  
		To create the "base" array type we simply create a prototype, zero 
		length in each dimension, array and use it to get its class 
		(Actually, I think there is a way we could do it with Class.forName() 
		but I don't trust this).   The code is simpler than the explanation...
		see below.
	*/
	private Object arrayNewInstance( 
		Class type, BSHArrayDimensions dimensionsNode, CallStack callstack )
		throws EvalError
	{
		if ( dimensionsNode.numUndefinedDims > 0 )
		{
            Object proto = Array.newInstance( 
				type, new int [dimensionsNode.numUndefinedDims] ); // zeros
			type = proto.getClass();
		}

        try {
            return Array.newInstance( 
				type, dimensionsNode.definedDimensions);
        } catch( NegativeArraySizeException e1 ) {
			throw new TargetError( e1, this, callstack );
        } catch( Exception e ) {
            throw new EvalError("Can't construct primitive array: " +
                e.getMessage(), this, callstack);
        }
	}
}
