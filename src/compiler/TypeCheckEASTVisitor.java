package compiler;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;
import static compiler.TypeRels.*;
import static compiler.TypeRels.lowestCommonAncestor;

import java.util.HashMap;
public class TypeCheckEASTVisitor extends BaseEASTVisitor<TypeNode,TypeException> {
	
	TypeCheckEASTVisitor() { super(true); } 					// enables incomplete tree exceptions 
	TypeCheckEASTVisitor(boolean debug) { super(true,debug); }	// enables print for debugging

	
	private TypeNode ckvisit(TypeNode t) throws TypeException {	//checks that a type object is visitable (not incomplete) 
		visit(t);
		return t;
	} 
	
	@Override
	public TypeNode visitNode(ProgLetInNode n) throws TypeException {
		if (print) printNode(n);
		TypeRels.superType = new HashMap<>();
		for (Node dec : n.declist)			// type-check all the declarations of the program
			try {
				visit(dec);
			} catch (IncomplException e) { 
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		return visit(n.exp);
	}

	@Override
	public TypeNode visitNode(ProgNode n) throws TypeException {
		if (print) printNode(n);
		return visit(n.exp);
	}
	
	@Override
	public TypeNode visitNode(PrintNode n) throws TypeException {
		if (print) printNode(n);
		return visit(n.exp);
	}
	
	@Override
	public TypeNode visitNode(IfNode n) throws TypeException {//OO O
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.cond), new BoolTypeNode())))
			throw new TypeException("Non boolean condition in if",n.getLine());
		TypeNode t = visit(n.th);	// type-check on then branch
		TypeNode e = visit(n.el);	// type-check on else branch
		TypeNode r = lowestCommonAncestor(t, e);//OO O: if-then-else possono essere utilizzati anche quando un'espressione è sottotipo dell'altra e quando hanno un lowest common ancestor
		if (r==null) {
			throw new TypeException("Incompatible types in then-else branches",n.getLine());	
		}
		return r;
	}
	
	@Override
	public TypeNode visitNode(IdNode n) throws TypeException {
		if (print) printNode(n);
		TypeNode t = visit(n.entry); 
		if(t instanceof MethodTypeNode)	// ID può essere di tipo funzionale ma non deve essere un metodo ; (non deve avere tipo "MethodTypeNode"in STentry);
			throw new TypeException("Wrong usage of method " + n.id,n.getLine());
		if (t instanceof ClassTypeNode)	// né deve essere il nome di una classe
			throw new TypeException("Wrong usage of class identifier " + n.id,n.getLine());
		return t;
	}
	
	///////////////////////////////////////// EXPRESSIONS
	
	@Override
	public TypeNode visitNode(TimesNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(PlusNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in sum",n.getLine());
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(MinusNode n) throws TypeException {	//LE
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(DivNode n) throws TypeException {		//LE
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(GreaterEqualNode n) throws TypeException {	//LE
		if (print) printNode(n);
		//secondo me è perchè adesso come tipi di ritorno posso avere anche tipi funzionali; ma su di essi la equal è un'operazione che non ha senso quindi la blocco 
		if (visit(n.left) instanceof ArrowTypeNode || visit(n.right) instanceof ArrowTypeNode)
			throw new TypeException("Cannot compare functional types ", n.getLine());
		if ( !(isSubtype(visit(n.left), visit(n.right)) || isSubtype(visit(n.right), visit(n.left))) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new  BoolTypeNode();//ritorna un boolean scema
	}
	
	@Override
	public TypeNode visitNode(LessEqualNode n) throws TypeException {	//LE
		if (print) printNode(n);
		//secondo me è perchè adesso come tipi di ritorno posso avere anche tipi funzionali; ma su di essi la equal è un'operazione che non ha senso quindi la blocco 
		if (visit(n.left) instanceof ArrowTypeNode || visit(n.right) instanceof ArrowTypeNode)
			throw new TypeException("Cannot compare functional types ", n.getLine());
		if ( !(isSubtype(visit(n.left), visit(n.right)) || isSubtype(visit(n.right), visit(n.left))) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new  BoolTypeNode();//ritorna un boolean scema
	}
	
	@Override
	public TypeNode visitNode(EqualNode n) throws TypeException {//HO
		if (print) printNode(n);
		TypeNode l = visit(n.left);
		TypeNode r = visit(n.right);
		if (l instanceof ArrowTypeNode || r instanceof ArrowTypeNode) //HO: non consente l'uso di espressioni con i tipi funzionali
			throw new TypeException("Cannot compare functional types ", n.getLine());	
		if ( !(isSubtype(l, r) || isSubtype(r, l)) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(NotNode n) throws TypeException {		//LE
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.val), new BoolTypeNode())))
			throw new TypeException("Non boolean in not",n.getLine());
		return new BoolTypeNode();
	}
	
	
	@Override
	public TypeNode visitNode(OrNode n) throws TypeException {		//LE
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new BoolTypeNode())
				&& isSubtype(visit(n.right), new BoolTypeNode())) )
			throw new TypeException("Incompatible types in or",n.getLine());
		return new BoolTypeNode();
	}	
	
	@Override
	public TypeNode visitNode(AndNode n) throws TypeException {		//LE
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new BoolTypeNode())			//uguale al controllo sulle operazioni ma, sui bool
				&& isSubtype(visit(n.right), new BoolTypeNode())) )
			throw new TypeException("Incompatible types in or",n.getLine());
		return new BoolTypeNode();
	}
	
	@Override
	public TypeNode visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return new BoolTypeNode();
	}
	
	@Override
	public TypeNode visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(CallNode n) throws TypeException { //OO
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry); 
		if ( !(t instanceof ArrowTypeNode || t instanceof MethodTypeNode) )	//OO: The type of call must be functional, otherwise a type-exception will be thrown 
			throw new TypeException("Invocation of a non-function "+n.id,n.getLine());
		
		ArrowTypeNode at= (ArrowTypeNode) t;
		
		if ( !(at.parlist.size() == n.arglist.size()))
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());
		return at.ret;
	}
	
	@Override
	public TypeNode visitNode(ClassCallNode n) throws TypeException {//OO: uguale a call node il symboltable visitor ha già controllato che id sia di tipo RefTypeNode
		if (print) printNode(n,n.classID);
		
		TypeNode t = visit(n.methodEntry); 
		
		if ( !(t instanceof MethodTypeNode) )
			throw new TypeException("Invocation of a non-method "+n.methodID,n.getLine());
		
		ArrowTypeNode at = ((MethodTypeNode) t).fun;
		
		if ( !(at.parlist.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.methodID,n.getLine());
		
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.methodID,n.getLine());
		return at.ret;
	}
	
	@Override
	public TypeNode visitNode(NewNode n) throws TypeException {	//OO: simile a CallNode - gli argomenti sono i campi della classe
		if (print) printNode(n,n.id);
		
		TypeNode typeNode = visit(n.entry); 
		if ( !(typeNode instanceof ClassTypeNode) )
			throw new TypeException("Instancing of a non-class "+n.id,n.getLine());
		
		ClassTypeNode classTypeNode = (ClassTypeNode) typeNode; //una volta che sono sicura che sia un classTypeNode devo creare la variabile giusta se no non posso accedere a allFields e allMethods
		if ( !(classTypeNode.allFields.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters to instance an object of class "+n.id,n.getLine());
		
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),classTypeNode.allFields.get(i))) )//controllo i parametri come callnode
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter to instance an object of class "+n.id,n.getLine());
		
		return new RefTypeNode(n.id);			//devo tornare un refTypeNode
	}
	
	//////////////////////////////////////DECLARATIONS
	
	@Override
	public TypeNode visitNode(FunNode n) throws TypeException {
		if (print) printNode(n,n.id);
		for (Node dec : n.declist)
			try {
				visit(dec);
			} catch (IncomplException e) { 
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) ) 
			throw new TypeException("Wrong return type for function " + n.id,n.getLine());
		return null;
	}

	@Override
	public TypeNode visitNode(VarNode n) throws TypeException {
		if (print) printNode(n,n.id);
		if ( !isSubtype(visit(n.exp),ckvisit(n.getType())) )
			throw new TypeException("Incompatible value for variable " + n.id,n.getLine());
		return null;
	}
	
	public TypeNode visitNode(MethodNode n) throws TypeException {	//OO: uguale a FunNode		
		if (print) printNode(n,n.id);
		for (Node dec : n.declist) {
			visit(dec);
		}
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) ) 
			throw new TypeException("Wrong return type for method " + n.id,n.getLine());
		return null;
	}
	
	@Override
	public TypeNode visitNode(ClassNode n) throws TypeException {//OO
		if (print) printNode(n,n.id);
		if(n.superID != null) {
			TypeRels.superType.put(n.id, n.superID);
			ClassTypeNode parentCT = (ClassTypeNode)n.superEntry.type;
			for(int i = 0; i<n.fields.size(); i++) {//scorre tipi in array allFields/allMethods del genitore controlla che il tipo alla stessa posizione nel proprio array sia sottotipo
				int position = -n.fields.get(i).offset-1;
				if(position < parentCT.allFields.size()) {
					if(!isSubtype(n.fields.get(i).getType(), parentCT.allFields.get(position))) {
						throw new TypeException("Fields must be subtype of the ones declared in superclass",n.getLine());
					}
				}
			}
			for(MethodNode method: n.methods) {													// non posso usare una lambda perchè devo lanciare un'eccezione e non ho voglia di importare la libreria 
				int position = method.offset;
				if(position < parentCT.allMethods.size()) {
					if ( !(isSubtype(((MethodTypeNode)method.getType()).fun, parentCT.allMethods.get(position))) ) {  // I metodi devono essere sottotipi di quelli dichiarati nella superclasse
						throw new TypeException("Wrong overriding of method " + method.id, n.getLine());
					}
				}
			}
		}
		return null;
	}

	@Override
	public TypeNode visitNode(BoolTypeNode n) {
		if (print) printNode(n);
		return null;
	}

	@Override
	public TypeNode visitNode(IntTypeNode n) {
		if (print) printNode(n);
		return null;
	}
	
	@Override
	public TypeNode visitNode(RefTypeNode n) {
		if (print) printNode(n);
		return n;
	}
	

	@Override
	public TypeNode visitNode(EmptyNode n) {
		if (print) printNode(n);
		return new EmptyTypeNode();				//torna semplicemente EmptyTypeNode
	}
	
	@Override
	public TypeNode visitNode(ArrowTypeNode n) throws TypeException {
		if (print) printNode(n);
		for (Node par: n.parlist) visit(par);
		visit(n.ret,"->"); //marks return type
		return null;
	}

	@Override
	public TypeNode visitNode(MethodTypeNode n) throws TypeException {
		if (print) printNode(n);
		visit(n.fun);
		return null;
	}
	
	@Override
	public TypeNode visitNode(ClassTypeNode n) throws TypeException {
		if (print) printNode(n);
		return null;
	}
	
	// STentry (ritorna campo type)
	@Override
	public TypeNode visitSTentry(STentry entry) throws TypeException {
		if (print) printSTentry("type");
		return ckvisit(entry.type); 
	}

}