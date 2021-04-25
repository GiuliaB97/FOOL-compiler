package compiler;

import java.util.*;
import compiler.lib.*;
//NON MODIFICATI
public class AST {
	
	public static class ProgLetInNode extends Node {
		final List<DecNode> declist;
		final Node exp;
		ProgLetInNode(List<DecNode> d, Node e) {
			declist = Collections.unmodifiableList(d); 
			exp = e;
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	public static class ProgNode extends Node {
		final Node exp;
		ProgNode(Node e) {exp = e;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class FunNode extends DecNode {// Versione HO: decommentato setType()
		final String id;
		final TypeNode retType;
		final List<ParNode> parlist;
		final List<DecNode> declist; 
		final Node exp;
		FunNode(String i, TypeNode rt, List<ParNode> pl, List<DecNode> dl, Node e) {
	    	id=i; 
	    	retType=rt; 
	    	parlist=Collections.unmodifiableList(pl); 
	    	declist=Collections.unmodifiableList(dl); 
	    	exp=e;
	    }
		
		void setType(ArrowTypeNode t) {type = t;} //Decommentato per estensione HO; mi serve per andare poi a settare il tipo nel symbol table visitor
		
		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	public static class ParNode extends DecNode {
		final String id;
		ParNode(String i, TypeNode t) {id = i; type = t;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class VarNode extends DecNode {
		final String id;
		final Node exp;
		VarNode(String i, TypeNode t, Node v) {id = i; type = t; exp = v;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
		
	public static class PrintNode extends Node {
		final Node exp;
		PrintNode(Node e) {exp = e;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class IfNode extends Node {
		final Node cond;
		final Node th;
		final Node el;
		IfNode(Node c, Node t, Node e) {cond = c; th = t; el = e;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class EqualNode extends Node {
		final Node left;
		final Node right;
		EqualNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class TimesNode extends Node {
		final Node left;
		final Node right;
		TimesNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class PlusNode extends Node {
		final Node left;
		final Node right;
		PlusNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class CallNode extends Node {
		final String id;
		final List<Node> arglist;
		STentry entry;
		int nl;
		CallNode(String i, List<Node> p) {
			id = i; 
			arglist = Collections.unmodifiableList(p);
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class IdNode extends Node {
		final String id;
		STentry entry;
		int nl;
		IdNode(String i) {id = i;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class BoolNode extends Node {
		final Boolean val;
		BoolNode(boolean n) {val = n;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class IntNode extends Node {
		final Integer val;
		IntNode(Integer n) {val = n;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class ArrowTypeNode extends TypeNode {
		final List<TypeNode> parlist;
		final TypeNode ret;
		ArrowTypeNode(List<TypeNode> p, TypeNode r) {
			parlist = Collections.unmodifiableList(p); 
			ret = r;
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class BoolTypeNode extends TypeNode {

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	public static class IntTypeNode extends TypeNode {

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	
	//////////////////////////////////////////////////// LANGUAGE EXTENSION NODES
	public static class GreaterEqualNode extends Node {
		final Node left;
		final Node right;
		GreaterEqualNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class LessEqualNode extends Node {
		final Node left;
		final Node right;
		LessEqualNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class NotNode extends Node {
		final Node val;
		NotNode(Node n) {val = n;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class MinusNode extends Node {
		final Node left;
		final Node right;
		MinusNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class OrNode extends Node {
		final Node left;
		final Node right;
		OrNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class DivNode extends Node {
		final Node left;
		final Node right;
		DivNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class AndNode extends Node {
		final Node left;
		final Node right;
		AndNode(Node l, Node r) {left = l; right = r;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	//////////////////////////////////////OO EXTENSION	
	//DICHIARAZIONI
	public static class FieldNode extends DecNode {//da specifica come ParNode
		final String id;
		int offset;		//Questo campo è l'unica differenza rispetto alla classe ParNode; mi serve in seguito
		FieldNode(String i, TypeNode t) {id = i; type=t;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	//da specifica come funNode;
	public static class MethodNode extends DecNode {
		final String id;
		final TypeNode retType;
		final List<ParNode> parlist;
		final List<DecNode> declist;
		final Node exp;
									//da qui sono campi che FunNode non ha:
		//int nl;					
		int offset;					//estensione OO: mi serve nel SymbolTableVisitor 
		String label;
		
		MethodNode(String i, TypeNode typeNode,  List<ParNode> parList2, List<DecNode> decList2, Node e) {
			id = i; 
			retType= typeNode;
			parlist = Collections.unmodifiableList(parList2);
			declist = Collections.unmodifiableList(decList2); 
			exp=e;
			//type = new MethodTypeNode(parlist.stream().map(p->p.getType()).collect(Collectors.toList()), retType);
	    }
		public void setType(TypeNode t) {type = t;}  // usato da SymbolTable durante la visita per settare il tipo "MethodTypeNode".
		
		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class ClassNode extends DecNode {
		final String id;
		final List<FieldNode> fields;
		final List<MethodNode> methods;
		String superID;					//mi serve per recuperare dalla hm del livello corrispondente(?)
		STentry superEntry=null;
	
		ClassNode(String i, List<FieldNode> f,  List<MethodNode> m, String si ) {
			id = i; 
			fields = Collections.unmodifiableList(f);
			methods = Collections.unmodifiableList(m);
			superID = si;
		}
		
		void setType(ClassTypeNode t) {type = t;}		
		void setSuperEntry(STentry e) {this.superEntry = e;}		//viene settato durante la visita del simboltable se la classe eredita
		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	//ESPRESSIONI
	public static class ClassCallNode extends Node {
		final String classID;
		final String methodID;
		final List<Node> arglist;
		STentry entry;
		STentry methodEntry;
		int nl;
		
		ClassCallNode(String c, String m, List<Node> p) {
			classID = c; 
			methodID = m;
			arglist = Collections.unmodifiableList(p);
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class ClassTypeNode extends TypeNode {
		List<TypeNode> allFields;			//richiesto da specifica
		List<MethodTypeNode> allMethods;	//richiesto da specifica
		
		ClassTypeNode(List<TypeNode> f, List<MethodTypeNode> m) {
			allFields = f;
			allMethods = m;
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	//simile a arrowNode
	public static class NewNode extends TypeNode {
		final String id;
		final List<Node> arglist;
		STentry entry;
		int nl;
		NewNode(String i, List<Node> p) {
			arglist = Collections.unmodifiableList(p); 
			id = i;
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class EmptyNode extends TypeNode {
		
		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	public static class MethodTypeNode extends TypeNode {
		final ArrowTypeNode fun;								//richaimato dal TypeCheckVisitor; al momento della visita
		MethodTypeNode(List<TypeNode> parTypelist, TypeNode r ) {
			fun = new ArrowTypeNode(parTypelist, r);
		}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
	
	//TIPI
	/*
 * A type that represents a reference to a class (i.e., type of variable is a class)
 * 
 */
	public static class RefTypeNode extends TypeNode { // var a:ClasseA;  ====> VarNode("a"):RefTypeNode("ClasseA");
		final String id;				//da specifica ha solo l'id nella classe come campo
		RefTypeNode(String i) {id = i;}

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}

	public static class EmptyTypeNode extends TypeNode {//non in AST ma restituito da typeCheck()di EmptyNode()

		@Override
		public <S,E extends Exception> S accept(BaseASTVisitor<S,E> visitor) throws E {return visitor.visitNode(this);}
	}
}