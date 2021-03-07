package compiler;

import compiler.AST.*;
import compiler.lib.*;
//nel progetto la relazione di subtype deve essere estesa per questo è stata presa sta roba da fool lib e messa qui ; fool lib io non posso toccarlo
public class TypeRels {

	// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base: IntTypeNode o BoolTypeNode
	public static boolean isSubtype(TypeNode a, TypeNode b) {
		return a.getClass().equals(b.getClass()) || ((a instanceof BoolTypeNode) && (b instanceof IntTypeNode));
	}

}
