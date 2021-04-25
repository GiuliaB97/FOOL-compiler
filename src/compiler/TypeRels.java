package compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import compiler.AST.*;
import compiler.lib.*;

public class TypeRels {

	static Map<String,String> superType;//mappa l'ID di classe nell'ID della sua superclasse; 


	//MOD HO e OO: gestione tipi funzionali: entrambi devono essere arrowtypenode con stesso numero di parametri e deve valere covarianza sul tipo di ritorno, e controvarianza sul tipo dei parametri
	public static boolean isSubtype(TypeNode a, TypeNode b) {
		if (a instanceof EmptyTypeNode && b instanceof RefTypeNode) {//MOD OO: EmptyTypeNode sottipo di qualsiasi RefTypeNode
			return true;
		}else if (a instanceof RefTypeNode && b instanceof RefTypeNode) {//controllo se a è sottoclasse di b
			RefTypeNode refA = (RefTypeNode)a;			//necessario se no no posso accedere a id
			RefTypeNode refB = (RefTypeNode)b;			//necessario se no no posso accedere a id
			if (refA.id.equals(refB.id)) {
				return true;
			}else {
				while(superType.containsKey(refA.id)) {
					String type = superType.get(refA.id);
					if(type.equals(refB.id)) {
						return true;
					}
				}
			}
			return false;
		}
		if (a instanceof MethodTypeNode && b instanceof MethodTypeNode)	{				// check per corretto overriding dei metodi
			ArrowTypeNode methodA = ((MethodTypeNode)a).fun;
			ArrowTypeNode methodB = ((MethodTypeNode)b).fun;
			if(methodA.parlist.size() != methodB.parlist.size()) {
				return false;
			}else if(!isSubtype(methodA.ret, methodB.ret)) {// covarianza dei return types: a.ret deve essere <= b.ret
				return false;
			}
			for(int i = 0; i < methodA.parlist.size(); i++) {// contro-varianza sul tipo dei parametri: a.par_i >= b.par_i
				if(!isSubtype(methodB.parlist.get(i), methodB.parlist.get(i))) {
					return false;
				}
			}
			return true;
		}

		if (a instanceof ArrowTypeNode && b instanceof ArrowTypeNode) {
			ArrowTypeNode arrowA = (ArrowTypeNode) a;
			ArrowTypeNode arrowB = (ArrowTypeNode) b;
			if(arrowA.parlist.size() != arrowB.parlist.size()) {
				return false;
			}else if(!isSubtype(arrowA.ret, arrowB.ret)) {// covarianza dei return types: a.ret deve essere <= b.ret
				return false;
			}
			for(int i = 0; i < arrowA.parlist.size(); i++) {// contro-varianza sul tipo dei parametri: a.par_i >= b.par_i
				if(!isSubtype(arrowB.parlist.get(i), arrowA.parlist.get(i))) 
					return false;
			}
			return true;
		}
		// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base: IntTypeNode o BoolTypeNode
		return a.getClass().equals(b.getClass()) || ((a instanceof BoolTypeNode) && (b instanceof IntTypeNode));
	}


	public static TypeNode lowestCommonAncestor(TypeNode a, TypeNode b) {
		if (a instanceof EmptyTypeNode && b instanceof RefTypeNode) {
			return b;
		}else if (b instanceof EmptyTypeNode && a instanceof RefTypeNode ) {
			return a;
		}

		if (a instanceof RefTypeNode && b instanceof RefTypeNode) {		//devo controllare se b è sottotipo di a o di una sua superclasse
			if(isSubtype(b,a))  return a;
			RefTypeNode refA = (RefTypeNode)a;			//necessario se no no posso accedere a id
			while (superType.containsKey(refA.id) ) {
				refA = new RefTypeNode(superType.get(refA.id));				//vado a recuperare la successiva superclasse
				if(isSubtype(b, refA)) 
					return refA;								//torno il RefTypeNode a alla superclasse
			}
		}

		if (a instanceof ArrowTypeNode && b instanceof ArrowTypeNode) {//HO-OO O: 
			ArrowTypeNode arrowA = (ArrowTypeNode) a;
			ArrowTypeNode arrowB = (ArrowTypeNode) b;

			if (arrowA.parlist.size() == arrowB.parlist.size()) {					
				TypeNode retType = lowestCommonAncestor(arrowA.ret, arrowB.ret);	// co-varianza del tipo di ritorno

				if( retType != null) {
					List<TypeNode> parTypes = new ArrayList<>();
					for(int i = 0; i < arrowA.parlist.size(); i++) {	// per ogni parametro prendo il tipo subType dell'altro - contro-varianza del tipo dei parametri
						if(isSubtype(arrowA.parlist.get(i), arrowB.parlist.get(i))) {
							parTypes.add(arrowA.parlist.get(i));
						}
						else if(isSubtype(arrowB.parlist.get(i), arrowA.parlist.get(i))) {
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
		return null;														//in ogni altro caso ritorno null
	}
}