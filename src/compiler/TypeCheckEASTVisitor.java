package compiler;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;
import static compiler.TypeRels.*;
import static compiler.TypeRels.lowestCommonAncestor;

/*Ottimizzazioni OO
 * Type Checking Più Efficiente per ClassNode
 * • Migliora l’efficienza nel type checking della dichiarazione delle classi
 * 	– effettua il controllo di correttezza (subtyping) solo per i campi/metodi 
 * 		su cui è stato fatto overriding
 * • Nuovo funzionamento type checking descritto in slide successiva:
 * 	– richiede di recuperare l’offset ed il tipo per ogni suo figlio campo o metodo
 * • aggiungere campo "offset" a FieldNode (come già fatto per MethodNode) e, 
 * 	nel symbol table visitor, settarlo a offset messo in STentry
 * • Si richiama sui figli che sono metodi (invariato)
 * • In caso di ereditarietà controlla che l'overriding sia corretto
 * 	– Chiamato parentCT il tipo (un ClassTypeNode) in "superEntry"; 
 * 		per ogni proprio figlio campo/metodo:
 * 		• calcola la posizione che, in allFields/allMethods di parentCT,
 * 			corrisponde al suo offset
 * 			– in nostri layouts: -offset-1 per campi e offset per metodi
 * 		• se la posizione è inferiore a lunghezza di allFields/allMethods di 
 * 			parentCT (overriding), controlla che il tipo del figlio sia
 * 			sottotipo del tipo in allFields/allMethods in tale posizione
 * 
 * Type Checking con Lowest Common Ancestor
 * • Rende possibile utilizzare nei rami then ed else di un "if-then-else" due espressioni
 * 	– non solo quando sono una sottotipo dell'altra,
 * 	– ma anche quando hanno un lowest common ancestor
 * 	• Type checking di IfNode
 * 		– chiama lowestCommonAncestor (nuovo metodo statico da aggiungere a TypeRels) 
 * 		sui tipi ottenuti per le espressioni nel then e nell'else:
 * 			• se ritorna null il typechecking fallisce, altrimenti restituisce 
 * 				il tipo ritornato
 *	• metodo:
 *		TypeNode lowestCommonAncestor(TypeNode a,TypeNode b)
 *		• per a e b tipi riferimento (o EmptyTypeNode)
 *			– se uno tra "a" e "b" è EmptyTypeNode torna l'altro; altrimenti
 *			– all'inizio considera la classe di "a" e risale, poi, le sue superclassi
 *				(tramite la funzione "superType") controllando, ogni volta, se "b"
 *				sia sottotipo (metodo "isSubtype") della classe considerata:
 *				• torna un RefTypeNode a tale classe qualora il controllo abbia, 
 *					prima o poi, successo, null altrimenti
 *				• per a e b tipi bool/int
 *					– torna int se almeno uno è int, bool altrimenti
 *				• in ogni altro caso torna null
 *
 *COMBINAZIONE HO E OO
 *• IdNode ID
 *	– ID può essere di tipo funzionale ma non deve essere un metodo 
 *		(non deve avere tipo "MethodTypeNode"in STentry); 
 *		né deve essere il nome di una classe
 *• IfNode (in versione OO con ottimizzazioni)
 *	– metodo lowestCommonAncestor di TypeRels esteso a tipi funzionali come 
 *		descritto nella slide successiva
 *• metodo:
 *	TypeNode lowestCommonAncestor(TypeNode a,TypeNode b)
 *	• per a e b tipi funzionali con stesso numero di parametri
 *		– controlla se esiste lowest common ancestor dei tipi di ritorno di a e b 
 *			(si chiama ricorsivamente) e se, per ogni i, i tipi parametro i-esimi 
 *			sono uno sottotipo dell'altro (metodo "isSubtype"):
 *	• torna null se il controllo non ha successo; altrimenti
 *	• torna un tipo funzionale che ha come tipo di ritorno il risultato della
 *		chiamata ricorsiva (covarianza) e come tipo di parametro i-esimo il
 *		tipo che è sottotipo dell'altro (controvarianza)
 */
public class TypeCheckEASTVisitor extends BaseEASTVisitor<TypeNode,TypeException> {
	
	TypeCheckEASTVisitor() { super(true); } // enables incomplete tree exceptions 
	TypeCheckEASTVisitor(boolean debug) { super(true,debug); } // enables print for debugging

	//checks that a type object is visitable (not incomplete) 
	private TypeNode ckvisit(TypeNode t) throws TypeException {
		visit(t);
		return t;
	} 
	
	@Override
	public TypeNode visitNode(ProgLetInNode n) throws TypeException {
		if (print) printNode(n);
 
		for (Node dec : n.declist)
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

	@Override
	public TypeNode visitNode(PrintNode n) throws TypeException {
		if (print) printNode(n);
		return visit(n.exp);
	}

	@Override
	public TypeNode visitNode(IfNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.cond), new BoolTypeNode())) )
			throw new TypeException("Non boolean condition in if",n.getLine());
		TypeNode t = visit(n.th);
		TypeNode e = visit(n.el);
		//if (isSubtype(t, e)) return e;
		//if (isSubtype(e, t)) return t;
		TypeNode r=lowestCommonAncestor(t, e);		//MOD: OTTIMIZZAZIONE
		if (r!=null)
			return r;
		else {
			throw new TypeException("Incompatible types in then-else branches",n.getLine());	
		}
	}

	
	@Override
	public TypeNode visitNode(EqualNode n) throws TypeException {
		if (print) printNode(n);
		TypeNode l = visit(n.left);
		TypeNode r = visit(n.right);
		//if ( !(isSubtype(l, r) || isSubtype(r, l)) )
		//	throw new TypeException("Incompatible types in equal",n.getLine());
		if (l instanceof ArrowTypeNode || r instanceof ArrowTypeNode)
			throw new TypeException("Cannot compare functional types ", n.getLine());
		if ( !(isSubtype(l, r) || isSubtype(r, l)) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new BoolTypeNode();
	}

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
////////////////////////////////////////////////////LANGUAGE EXTENSION NODES
	
	@Override
	public TypeNode visitNode(MinusNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(DivNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(GreaterEqualNode n) throws TypeException {
		if (print) printNode(n);
		//if ( !(isSubtype(visit(n.left), new IntTypeNode())&& isSubtype(visit(n.right), new IntTypeNode())) )	throw new TypeException("Non integers in greater equal",n.getLine());
		if (visit(n.left) instanceof ArrowTypeNode || visit(n.right) instanceof ArrowTypeNode)
			throw new TypeException("Cannot compare functional types ", n.getLine());
		if ( !(isSubtype(visit(n.left), visit(n.right)) || isSubtype(visit(n.right), visit(n.left))) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(LessEqualNode n) throws TypeException {
		if (print) printNode(n);
		//if ( !(isSubtype(visit(n.left), new IntTypeNode())&& isSubtype(visit(n.right), new IntTypeNode())) )throw new TypeException("Non integers in less equal",n.getLine());
		if (visit(n.left) instanceof ArrowTypeNode || visit(n.right) instanceof ArrowTypeNode)
			throw new TypeException("Cannot compare functional types ", n.getLine());
		if ( !(isSubtype(visit(n.left), visit(n.right)) || isSubtype(visit(n.right), visit(n.left))) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(AndNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new BoolTypeNode())
				&& isSubtype(visit(n.right), new BoolTypeNode())) )
			throw new TypeException("Incompatible types in or",n.getLine());
		return new BoolTypeNode();
	}
	
	@Override
	public TypeNode visitNode(OrNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new BoolTypeNode())
				&& isSubtype(visit(n.right), new BoolTypeNode())) )
			throw new TypeException("Incompatible types in or",n.getLine());
		return new BoolTypeNode();
	}	
	
	@Override
	public TypeNode visitNode(NotNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.val), new BoolTypeNode())))
			throw new TypeException("Non integers in not",n.getLine());
		return new BoolTypeNode();
	}
//////////////////////////////////////////////////////////////////////////
	public TypeNode visitNode(MethodNode n) throws TypeException {
		if (print) printNode(n,n.id);
		for (Node dec : n.declist)
			try {
				visit(dec);
			} catch (IncomplException e) { 
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) ) 
			throw new TypeException("Wrong return type for method " + n.id,n.getLine());
		return null;
	}
	@Override
	public TypeNode visitNode(ClassCallNode n) throws TypeException {
		if (print) printNode(n,n.classId);
		TypeNode t = visit(n.methodEntry); 
		if ( !(t instanceof MethodTypeNode) )
			throw new TypeException("Invocation of a non-method "+n.methodId,n.getLine());
		ArrowTypeNode at = ((MethodTypeNode) t).fun;
		if ( !(at.parlist.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.methodId,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.methodId,n.getLine());
		return at.ret;
	}
	@Override
	public TypeNode visitNode(EmptyNode n) {
		if (print) printNode(n);
		return new EmptyTypeNode();
	}
	@Override
	public TypeNode visitNode(ClassNode n) throws TypeException {
		if (print) printNode(n,n.id);
		if(n.superID != null) {
			TypeRels.superType.put(n.id, n.superID);
			ClassTypeNode parentCT = (ClassTypeNode)n.superEntry.type;
			for(int i = 0; i<n.fieldlist.size(); i++) {
				int position = -n.fieldlist.get(i).offset-1;
				if(position < parentCT.fields.size()) {
					if(!isSubtype(n.fieldlist.get(i).getType(), parentCT.fields.get(position))) {
						throw new TypeException("Fields must be subtype of the ones declared in superclass",n.getLine());
					}
				}
			}
			for(int i = 0; i<n.methodlist.size(); i++) {
				int position = n.methodlist.get(i).offset;
				if(position < parentCT.methods.size()) {
						throw new TypeException("Methods must be subtype of the ones declared in superclass",n.getLine());
				}
			}
		}
		for (MethodNode m : n.methodlist) {
			try {
				visit(m);
			} catch (IncomplException e) { 
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		}
		return null;
	}

	@Override
	public TypeNode visitNode(CallNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry); 
		
		if ( !(t instanceof ArrowTypeNode || t instanceof MethodTypeNode) )
			throw new TypeException("Invocation of a non-function "+n.id,n.getLine());
		ArrowTypeNode at= (ArrowTypeNode) t;
		
		if ( !(at.parlist.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());
		return at.ret;
	}
	
	@Override
	public TypeNode visitNode(NewNode n) throws TypeException {			// simile a CallNode - gli argomenti sono i campi della classe
		if (print) printNode(n,n.id);
		
		TypeNode t = visit(n.entry); 
		if ( !(t instanceof ClassTypeNode) )
			throw new TypeException("Instancing of a non-class "+n.id,n.getLine());
		
		ClassTypeNode ct = (ClassTypeNode) t;
		if ( !(ct.fields.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters to instance an object of class "+n.id,n.getLine());
		
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),ct.fields.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter to instance an object of class "+n.id,n.getLine());
		
		return new RefTypeNode(n.id);
	}
	@Override
	public TypeNode visitNode(IdNode n) throws TypeException {
		if (print) printNode(n,n.id);
		if(n.entry.type instanceof MethodTypeNode)
			throw new TypeException("Wrong usage of method " + n.id,n.getLine());
		TypeNode t = visit(n.entry); 
		if (t instanceof ClassTypeNode)
			throw new TypeException("Wrong usage of class identifier " + n.id,n.getLine());
		return t;
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

// gestione tipi incompleti	(se lo sono lancia eccezione)
	
	@Override
	public TypeNode visitNode(ArrowTypeNode n) throws TypeException {
		if (print) printNode(n);
		for (Node par: n.parlist) visit(par);
		visit(n.ret,"->"); //marks return type
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

// STentry (ritorna campo type)

	@Override
	public TypeNode visitSTentry(STentry entry) throws TypeException {
		if (print) printSTentry("type");
		return ckvisit(entry.type); 
	}

}