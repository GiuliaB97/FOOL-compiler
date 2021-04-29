package compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import compiler.AST.*;
import compiler.lib.*;
/**
 * Class responsible for subtypes checks.
 * 
 * @author giuliabrugnatti
 *
 */
public class TypeRels {

	static Map<String,String> superType;//mappa l'ID di classe nell'ID della sua superclasse; 
	
	/**
	 * Method that checks if the first expression is subtype of the second.
	 * 
	 * @param a is a type node
	 * @param b is a type node
	 * @return
	 */
	public static boolean isSubtype(TypeNode a, TypeNode b) {//MOD HO e OO: functional types management: entrambi devono essere arrowtypenode con stesso numero di parametri e deve valere covarianza sul tipo di ritorno, e controvarianza sul tipo dei parametri
		
		if (a instanceof EmptyTypeNode && b instanceof RefTypeNode) {//MOD OO: EmptyTypeNode subtype of RefTypeNode (no matter which is it)
			return true;
		}else if (a instanceof RefTypeNode && b instanceof RefTypeNode) {
			RefTypeNode refA = (RefTypeNode)a;	//Correct class instantiation is needed to be able to access to id field
			RefTypeNode refB = (RefTypeNode)b;			
			if (refA.id.equals(refB.id)) {
				return true;
			}else {
				while(superType.containsKey(refA.id)) {		//If the expression has a supertype
					String type = superType.get(refA.id);	//it gets it from the map
					if(type.equals(refB.id)) {				//and compares it from the one of the second expression
						return true;
					}
				}
			}
			return false;
		}
		if (a instanceof MethodTypeNode && b instanceof MethodTypeNode)	{// it checks the correct overriding of the methods
			ArrowTypeNode methodA = ((MethodTypeNode)a).fun;
			ArrowTypeNode methodB = ((MethodTypeNode)b).fun;
			if(methodA.parlist.size() != methodB.parlist.size()) {//they must have the same number of parameters
				return false;
			}else if(!isSubtype(methodA.ret, methodB.ret)) {// covariance on the return types: a.ret must be <= b.ret
				return false;
			}
			for(int i = 0; i < methodA.parlist.size(); i++) {// contravariance on the parameters types: a.par_i >= b.par_i
				if(!isSubtype(methodB.parlist.get(i), methodB.parlist.get(i))) {
					return false;
				}
			}
			return true;
		}

		if (a instanceof ArrowTypeNode && b instanceof ArrowTypeNode) {
			ArrowTypeNode arrowA = (ArrowTypeNode) a;
			ArrowTypeNode arrowB = (ArrowTypeNode) b;
			if(arrowA.parlist.size() != arrowB.parlist.size()) {//they must have the same number of parameters
				return false;
			}else if(!isSubtype(arrowA.ret, arrowB.ret)) {// covariance on the return types: a.ret must be <= b.ret
				return false;
			}
			for(int i = 0; i < arrowA.parlist.size(); i++) {// contravariance on the parameters types: a.par_i >= b.par_i
				if(!isSubtype(arrowB.parlist.get(i), arrowA.parlist.get(i))) 
					return false;
			}
			return true;
		}
		// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base: IntTypeNode o BoolTypeNode
		return a.getClass().equals(b.getClass()) || ((a instanceof BoolTypeNode) && (b instanceof IntTypeNode));
	}

	/**
	 * Methods that checks if the two expressions have a common ancestors
	 * @param a is a type node
	 * @param b is a type node
	 * @return null if they do not have a common ancestor, or the common ancestor type node
	 */
	public static TypeNode lowestCommonAncestor(TypeNode a, TypeNode b) {
		if (a instanceof EmptyTypeNode && b instanceof RefTypeNode) {//MOD OO: EmptyTypeNode subtype of RefTypeNode (no matter which is it)
			return b;
		}else if (b instanceof EmptyTypeNode && a instanceof RefTypeNode ) {//MOD OO: EmptyTypeNode subtype of RefTypeNode (no matter which is it)
			return a;
		}

		if (a instanceof RefTypeNode && b instanceof RefTypeNode) {		//it checks if b is subtype of a, or its superclass (if it has one)
			if(isSubtype(b,a))  return a;
			RefTypeNode refA = (RefTypeNode)a;	
			while (superType.containsKey(refA.id) ) {
				refA = new RefTypeNode(superType.get(refA.id));	//it retrieves the superclass
				if(isSubtype(b, refA)) {
					return refA;								//return the RefTypeNode of the superclass
				}
			}
		}

		if (a instanceof ArrowTypeNode && b instanceof ArrowTypeNode) {//HO-OO O: 
			ArrowTypeNode arrowA = (ArrowTypeNode) a;
			ArrowTypeNode arrowB = (ArrowTypeNode) b;

			if (arrowA.parlist.size() == arrowB.parlist.size()) {					
				TypeNode retType = lowestCommonAncestor(arrowA.ret, arrowB.ret);	// covariance of the return type

				if( retType != null) {
					List<TypeNode> parTypes = new ArrayList<>();
					for(int i = 0; i < arrowA.parlist.size(); i++) {	// for each parameter it checks if the first is subtype of the second - contravariance on the parameter type
						if(isSubtype(arrowA.parlist.get(i), arrowB.parlist.get(i))) {
							parTypes.add(arrowA.parlist.get(i));
						}
						else if(isSubtype(arrowB.parlist.get(i), arrowA.parlist.get(i))) {// for each parameter it checks if the second is subtype of the first - contravariance on the parameter type
							parTypes.add(arrowB.parlist.get(i));
						}
						else {
							return null;
						}
					}

					return new ArrowTypeNode(parTypes,retType);
				}
			}
		}
		return null;
	}
}