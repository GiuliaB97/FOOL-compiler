package compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import compiler.AST.*;
import compiler.lib.*;
/**
 * Class responsible for subtypes checks.
 *
 */
public class TypeRels {

	static Map<String,String> superType;//mappa l'ID di classe nell'ID della sua superclasse; 
	
	/**
	 * Method that checks if the first expression is sub-type of the second.
	 *- if a or b is an 'emptyType' node it returns the other one;
	 - if a and b are both 'refType' nodes it checks if b is sub-type of a of any of its ancestors;
	 * - if a and b are both 'arrowType' nodes: it should return a 'refType' node that has:
	 * 	- the commonAncestor between the nodes as return value (covariance);
	 * 	- a list of parameter; obtained by comparing the th-parameter of two nodes and adding to the list the one that is sub-type of the other.
	 *  
	 * @param a is a type node
	 * @param b is a type node
	 * @return
	 */
	public static boolean isSubtype(TypeNode a, TypeNode b) {//MOD HO e OO: functional types management: entrambi devono essere arrowtypenode con stesso numero di parametri e deve valere covarianza sul tipo di ritorno, e controvarianza sul tipo dei parametri
		
		if (a instanceof EmptyTypeNode && b instanceof RefTypeNode) {//OO: EmptyTypeNode subtype of RefTypeNode (no matter which is it)
			return true;
		}else if (b instanceof EmptyTypeNode && a instanceof RefTypeNode ) {//OO: EmptyTypeNode subtype of RefTypeNode (no matter which is it)
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
		
		if (a instanceof ArrowTypeNode && b instanceof ArrowTypeNode){// it checks the correct overriding of the methods
			ArrowTypeNode arrowA =(ArrowTypeNode) a;
			ArrowTypeNode arrowB =(ArrowTypeNode) b;

			if(arrowA.parlist.size() != arrowB.parlist.size()) {//they must have the same number of parameters
				return false;
			}else if(!isSubtype(arrowA.ret, arrowB.ret)) {// covariance on the return types: a.ret must be <= b.ret
				return false;
			}
			for(int i = 0; i < arrowA.parlist.size(); i++) {// contravariance on the parameters types: a.par_i >= b.par_i
				if(!isSubtype(arrowB.parlist.get(i), arrowB.parlist.get(i))) {
					return false;
				}
			}
			return true;
		}
		
		// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base: IntTypeNode o BoolTypeNode
		return a.getClass().equals(b.getClass()) || ((a instanceof BoolTypeNode) && (b instanceof IntTypeNode));
	}

	/**
	 * Methods that checks if the two expressions have a common ancestor.
	 * According to type rules of the language two nodes have a common ancestor if one of this conditions is satisfied:
	 * - if a or b is an 'emptyType' node it returns the other one;
	 * - if a or b is a 'boolType' node and the other an "intType" node it returns an "intType" node, as well as they are both 'intType';
	 * - if a and b are 'boolType' nodes, then it returns a 'boolType';
	 * - if a and b are both 'refType' nodes it checks if b is sub-type of a of any of its ancestors;
	 * - if a and b are both 'arrowType' nodes: it should return a 'refType' node that has:
	 * 	- the commonAncestor between the nodes as return value (covariance);
	 * 	- a list of parameter; obtained by comparing the th-parameter of two nodes and adding to the list the one that is sub-type of the other.
	 * 
	 * @param a is a type node
	 * @param b is a type node
	 * @return null if they do not have a common ancestor, or the common ancestor type node
	 */
	public static TypeNode lowestCommonAncestor(TypeNode a, TypeNode b) {
		if (a instanceof EmptyTypeNode && b instanceof RefTypeNode) {//OO: EmptyTypeNode subtype of RefTypeNode (no matter which is it)
			return b;
		}else if (b instanceof EmptyTypeNode && a instanceof RefTypeNode ) {//OO: EmptyTypeNode subtype of RefTypeNode (no matter which is it)
			return a;
		}
		
		if(a instanceof IntTypeNode || a instanceof BoolTypeNode && b instanceof IntTypeNode || b instanceof BoolTypeNode) {
			if(a instanceof IntTypeNode || b instanceof IntTypeNode) {
				return new IntTypeNode();
			} else {
				return new BoolTypeNode();
			}
		}
		
		if (a instanceof RefTypeNode && b instanceof RefTypeNode) {		//it checks if b is subtype of a, or its superclass (if it has one)
			if(isSubtype(b,a))  return a;
			RefTypeNode refA = (RefTypeNode)a;	
			while (superType.containsKey(refA.id) ) {			//it tries to discover if there is any relation between a and b; by ascending the ancestors of a and checking if b is sub-type of any of them
				refA = new RefTypeNode(superType.get(refA.id));	//it retrieves the superclass
				if(isSubtype(b, refA)) {
					return refA;								//return the RefTypeNode of the superclass if it finds it
				}
			}
		}

		if (a instanceof ArrowTypeNode && b instanceof ArrowTypeNode) {//HO-OO O: 
			ArrowTypeNode arrowA = (ArrowTypeNode) a;
			ArrowTypeNode arrowB = (ArrowTypeNode) b;

			if (arrowA.parlist.size() == arrowB.parlist.size()) {					
				TypeNode retType = lowestCommonAncestor(arrowA.ret, arrowB.ret);// covariance of the return type; they must be sub-type of type declared 
				if( retType != null) {
					List<TypeNode> parTypes = new ArrayList<>();
					for(int i = 0; i < arrowA.parlist.size(); i++) {	//contravariance of the parameters 
						if(isSubtype(arrowA.parlist.get(i), arrowB.parlist.get(i))) {//for each parameter it checks if the first is subtype of the second - contravariance on the parameter type
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