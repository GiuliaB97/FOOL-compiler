package compiler;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;
import static compiler.TypeRels.*;
import static compiler.TypeRels.lowestCommonAncestor;

import java.util.HashMap;
/**
 * Class responsible of the type-check operations of the program.
 * Each method checks if the expression, declaration, etc. that is passed as argument respect the related type rules of the language.
 * If it is compliant with the aforementioned rules it returns its type, otherwise it throws a type exception.
 *
 */
public class TypeCheckEASTVisitor extends BaseEASTVisitor<TypeNode,TypeException> {

	TypeCheckEASTVisitor() { super(true); } 					// enables incomplete tree exceptions
	TypeCheckEASTVisitor(boolean debug) { super(true,debug); }	// enables print for debugging


	private TypeNode ckvisit(TypeNode t) throws TypeException {	//checks that a type object is visitable (not incomplete)
		visit(t);
		return t;
	}
	///////////////////////////////////////////DECLARATIONS
	/**
	 * Method that checks if a "let-in" program respects the type rules of the language.
	 * */
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

	/**
	 * Method that checks the type rules in a program without declarations.
	 */
	@Override
	public TypeNode visitNode(ProgNode n) throws TypeException {
		if (print) printNode(n);
		return visit(n.exp);
	}

	/**
	 * Method responsible for type-check of a function's declaration.
	 * According to the type checking rules a function result correct, if:
	 * - its return value is sub-type of the one of its declaration and it is not incomplete;
	 * - its declarations respect the type rules of the language.
	 */
	@Override
	public TypeNode visitNode(FunNode n) throws TypeException {
		if (print) printNode(n,n.id);
		for (Node dec : n.declist)//First, it visits all the declaration of the function accumulating the exceptions they could cause
			try {
				visit(dec);
			} catch (IncomplException e) {
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) )// next, it checks if the return type of the function is not incomplete and if return type of the body of the function it is sub-type of that.
			throw new TypeException("Wrong return type for function " + n.id,n.getLine());
		return null;
	}

	/**
	 * Method responsible for type-check of a variable's declaration.
	 * To be compliant with the language rules the value of variable must be sub-type 
	 * of the one of its declaration and it is not incomplete.
	 */
	@Override
	public TypeNode visitNode(VarNode n) throws TypeException {
		if (print) printNode(n,n.id);
		if ( !isSubtype(visit(n.exp),ckvisit(n.getType())) )// It checks if the initialization expression of the variable's type is sub-type of type with whom it is declared (and that last it is not incomplete).
			 
			throw new TypeException("Incompatible value for variable " + n.id,n.getLine());
		return null;
	}

	/**
	 * Method responsible for type-check of a method's declaration.
	 * According to the type checking rules a method result correct, if:
	 * - its return value is sub-type of the one of its declaration and it is not incomplete;
	 * - its declarations respect the type rules of the language.
	 */
	public TypeNode visitNode(MethodNode n) throws TypeException {	//OO: equal to FunNode
		if (print) printNode(n,n.id);
		for (Node dec : n.declist) {
			visit(dec);
		}
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) )
			throw new TypeException("Wrong return type for method " + n.id,n.getLine());
		return null;
	}

	/**
	 * Method responsible for type-check of a class' declaration.
	 * The type check operation of the class consists of two main phases: 
	 *	- in the first one, the method checks if the inherited/overridden fields and methods respect the type rules of the language;
	 *	- in the second one, the method must execute a visit on its own methods;
	 */
	@Override
	public TypeNode visitNode(ClassNode n) throws TypeException {//OO
		if (print) printNode(n,n.id);
		int position;
		if(n.superID != null) {
			TypeRels.superType.put(n.id, n.superID);
			ClassTypeNode parentCT = (ClassTypeNode)n.superEntry.type;
			for(int i = 0; i<n.fields.size(); i++) {
				
				position =-n.fields.get(i).offset-1;	//fields are on the stack which grows downwards;  NB because of field's layout: position= -offset-1
				if(position < parentCT.allFields.size()) {	//it is checking just if a field is trying to override in a wrong way a field of its superclass so it must stop when the superclass' fields are finished
					if(!isSubtype(n.fields.get(i).getType(), parentCT.allFields.get(position))) {
						throw new TypeException("Fields must be subtype of the ones declared in superclass",n.getLine());
					}
				}
			}
			
			for(int i = 0; i<n.methods.size(); i++) {
				position = n.methods.get(i).offset;//NB because of method's layout: position = offset
				if(position < parentCT.allMethods.size()) {
					if(!isSubtype(n.methods.get(i).getType(), parentCT.allMethods.get(position))) {
						throw new TypeException("Methods must be subtype of the ones declared in superclass",n.getLine());
					}
				}
			}
		}
		
		for (MethodNode m : n.methods) {						// Before it ends, it must execute a visit on its, method if not they will not be type-checked
			try {
				visit(m);
			} catch (IncomplException e) { 
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		}
		return null;
	}
	
	///////////////////////////////////////// EXPRESSIONS
	/**
	 * Method responsible for type-check of a '*' expression.
	 * According to the rules of the language, a '*' expression can be used only with integer operators.
	 */
	@Override
	public TypeNode visitNode(TimesNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}

	/**
	 * Method responsible for type-check of a '+' expression.
	 * According to the rules of the language, a '+' expression can be used only with integer operators.
	 *
	 */
	@Override
	public TypeNode visitNode(PlusNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in sum",n.getLine());
		return new IntTypeNode();
	}

	/**
	 * Method responsible for type-check of a '-' expression.
	 * According to the rules of the language, a '-' expression can be used only with integer operators.
	 */
	@Override
	public TypeNode visitNode(MinusNode n) throws TypeException {	//LE
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}

	/**
	 * Method responsible for type-check of a '/' expression.
	 * According to the rules of the language, a '/' expression can be used only with integer operators.
	 */
	@Override
	public TypeNode visitNode(DivNode n) throws TypeException {		//LE
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}

	/**
	 * Method responsible for type-check of a '>=' expression.
	 * To be compliant with the type rules of the language a '>=' expression 
	 * cannot be used with functional types and it must be used only if 
	 * its operators are one sub-type of the other
	 */
	@Override
	public TypeNode visitNode(GreaterEqualNode n) throws TypeException {	//LE
		if (print) printNode(n);
		if (visit(n.left) instanceof ArrowTypeNode || visit(n.right) instanceof ArrowTypeNode)
			throw new TypeException("Cannot compare functional types ", n.getLine());
		if ( !(isSubtype(visit(n.left), visit(n.right)) || isSubtype(visit(n.right), visit(n.left))) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new  BoolTypeNode();//ritorna un boolean scema
	}

	/**
	 * Method responsible for type-check of a '<=' expression.
	 * To be compliant with the type rules of the language a '<=' expression 
	 * cannot be used with functional types and it must be used only 
	 * if its operators are one sub-type of the other.
	 */
	@Override
	public TypeNode visitNode(LessEqualNode n) throws TypeException {	//LE
		if (print) printNode(n);
		if (visit(n.left) instanceof ArrowTypeNode || visit(n.right) instanceof ArrowTypeNode)
			throw new TypeException("Cannot compare functional types ", n.getLine());
		if ( !(isSubtype(visit(n.left), visit(n.right)) || isSubtype(visit(n.right), visit(n.left))) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new  BoolTypeNode();//ritorna un boolean scema
	}

	/**
	 * Method responsible for type-check of an '==' expression.
	 * To be compliant with the type rules of the language an  '==' expression 
	 * cannot be used with functional types and it must be used only if 
	 * its operators are one sub-type of the other.
	 */	@Override
	 public TypeNode visitNode(EqualNode n) throws TypeException {//HO
		 if (print) printNode(n);
		 TypeNode l = visit(n.left);
		 TypeNode r = visit(n.right);
		 if (l instanceof ArrowTypeNode || r instanceof ArrowTypeNode) //HO
			 throw new TypeException("Cannot compare functional types ", n.getLine());
		 if ( !(isSubtype(l, r) || isSubtype(r, l)) )
			 throw new TypeException("Incompatible types in equal",n.getLine());
		 return new BoolTypeNode();
	 }

	 /**
	  * Method responsible for type-check of a '!' expression.
	  * According to the rules of the language, a '!' expression can be used only with a boolean operator.
	  */
	 @Override
	 public TypeNode visitNode(NotNode n) throws TypeException {		//LE
		 if (print) printNode(n);
		 if ( !(isSubtype(visit(n.val), new BoolTypeNode())))
			 throw new TypeException("Non boolean in not",n.getLine());
		 return new BoolTypeNode();
	 }
	 
	 /**
	  * Method responsible for type-check of an '&&' expression.
	  * According to the rules of the language, an '&&' expression can be used only with boolean operators.
	  *
	  */
	 @Override
	 public TypeNode visitNode(AndNode n) throws TypeException {		//LE
		 if (print) printNode(n);
		 if ( !(isSubtype(visit(n.left), new BoolTypeNode())			//uguale al controllo sulle operazioni ma, sui bool
				 && isSubtype(visit(n.right), new BoolTypeNode())) )
			 throw new TypeException("Incompatible types in or",n.getLine());
		 return new BoolTypeNode();
	 }

	 /**
	  * Method responsible for type-check of an '||' expression.
	  * According to the rules of the language, an '||' expression can be used only with boolean operators.
	  */
	 @Override
	 public TypeNode visitNode(OrNode n) throws TypeException {		//LE
		 if (print) printNode(n);
		 if ( !(isSubtype(visit(n.left), new BoolTypeNode())
				 && isSubtype(visit(n.right), new BoolTypeNode())) )
			 throw new TypeException("Incompatible types in or",n.getLine());
		 return new BoolTypeNode();
	 }
	 
	 /**
	 * Method for the type-check of a integer expression.
	 */
	 @Override
	 public TypeNode visitNode(IntNode n) {
		 if (print) printNode(n,n.val.toString());
		 return new IntTypeNode();
	 }
	 
	/**
	 * Method for the type-check of a boolean expression.
	 */
	 @Override
	 public TypeNode visitNode(BoolNode n) {
		 if (print) printNode(n,n.val.toString());
		 return new BoolTypeNode();
	 }
	 
	/**
	 * Method for the type-check of a empty expression.
	 */
	 @Override
	 public TypeNode visitNode(EmptyNode n) {// OO
		 if (print) printNode(n);
		 return new EmptyTypeNode();	
	 }
	 /**
	  * Method responsible for type-check of a 'call' expression.
	  * According to the rules of the language, the type of call must be functional, otherwise a type-exception must be thrown,
	  * next, it must check if the expression is compliant with its declaration by checking if it has the same number of parameters and
	  * if they are are sub-type of the ones declared.
	  */
	 @Override
	 public TypeNode visitNode(CallNode n) throws TypeException { //OO
		 if (print) printNode(n,n.id);
		 TypeNode t = visit(n.entry); //downcast
		 if ( !(t instanceof ArrowTypeNode || t instanceof MethodTypeNode) )	//OO
			 throw new TypeException("Invocation of a non-function "+n.id,n.getLine());

		 ArrowTypeNode at= (ArrowTypeNode) t;

		 if ( !(at.parlist.size() == n.arglist.size()))
			 throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		 for (int i = 0; i < n.arglist.size(); i++)
			 if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				 throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());
		 return at.ret;
	 }

	 /**
	  * Method responsible for type-check of a "method call" expression.
	  * According to the rules of the language, the type of method call must be a MethodTypeNode, 
	  * next, it must check if the expression is compliant with its declaration by checking if it has the same number of parameters and
	  * if they are are sub-type of the ones declared.
	  */
	 @Override
	 public TypeNode visitNode(ClassCallNode n) throws TypeException {
		 if (print) printNode(n,n.classID);

		 TypeNode t = visit(n.methodEntry);

		 if ( !(t instanceof MethodTypeNode) )
			 throw new TypeException("Invocation of a non-method "+n.methodID,n.getLine());

		 //OO: hereinafter it is equal the previous method (callNode) because the symbol table visitor has already checked that it is a RefTypeNode
		 ArrowTypeNode at = ((MethodTypeNode) t).fun;

		 if ( !(at.parlist.size() == n.arglist.size()) )
			 throw new TypeException("Wrong number of parameters in the invocation of "+n.methodID,n.getLine());

		 for (int i = 0; i < n.arglist.size(); i++)
			 if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				 throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.methodID,n.getLine());
		 return at.ret;
	 }

	 /**
	  * Method responsible for type-check of a new object instantiation.
	  * According to the rules of the language, the type of new object must be a ClassTypeNode, 
	  * and it must be instantiated with the correct** number of arguments (that must also be of the correct** type).
	  * 
	  * **depending on the class that the node is instantiating.
	  */
	 @Override
	 public TypeNode visitNode(NewNode n) throws TypeException {	//OO: like CallNode but the type-check is on the fields of the class instead of on the parameters
		 if (print) printNode(n,n.id);

		 TypeNode typeNode = visit(n.entry);						//The only way that is has to check if the object instantiated respect the type rules is to get its type from its entry field
		 if ( !(typeNode instanceof ClassTypeNode) )					//otherwise a type-exception must be thrown
			 throw new TypeException("Instancing of a non-class "+n.id,n.getLine());

		 ClassTypeNode classTypeNode = (ClassTypeNode) typeNode;
		 if ( !(classTypeNode.allFields.size() == n.arglist.size()) )
			 throw new TypeException("Wrong number of parameters to instance an object of class "+n.id,n.getLine());

		 for (int i = 0; i < n.arglist.size(); i++)
			 if ( !(isSubtype(visit(n.arglist.get(i)),classTypeNode.allFields.get(i))) )
				 throw new TypeException("Wrong type for "+(i+1)+"-th parameter to instance an object of class "+n.id,n.getLine());

		 return new RefTypeNode(n.id);			//It must return refTypeNode
	 }


	 /**
	  * Method that responsible for the management of the "print" expression.
	  */
	 @Override
	 public TypeNode visitNode(PrintNode n) throws TypeException {
		 if (print) printNode(n);
		 return visit(n.exp);
	 }

	 /**
	  * Method responsible of the type-check of an "if-then-else" expression.
	  * According to the type rules of the program an if-then-else expression respect the type rules if
	  * its condition is a boolean type and its expressions are one sub-type of the other or, 
	  * they have a lowest common ancestors.
	  */
	 @Override
	 public TypeNode visitNode(IfNode n) throws TypeException {//OO O
		 if (print) printNode(n);
		 if ( !(isSubtype(visit(n.cond), new BoolTypeNode())))
			 throw new TypeException("Non boolean condition in if",n.getLine());
		 TypeNode t = visit(n.th);	// type-check on then branch
		 TypeNode e = visit(n.el);	// type-check on else branch
		 TypeNode r = lowestCommonAncestor(t, e);//OO O
		 if (r==null) {
			 throw new TypeException("Incompatible types in then-else branches",n.getLine());
		 }
		 return r;
	 }

	 /**
	  * Method responsible for type-check of an "identifier" expression.
	  * According to the rules of the language, an "identifier" can be a functional type,
	  * but it cannot be a MethodTypeNode or a ClassTypeNode.
	  */
	 @Override
	 public TypeNode visitNode(IdNode n) throws TypeException {//OO
		 if (print) printNode(n);
		 TypeNode t = visit(n.entry);
		 if(t instanceof MethodTypeNode)	// OO
			 throw new TypeException("Wrong usage of method " + n.id,n.getLine());
		 if (t instanceof ClassTypeNode)	// OO
			 throw new TypeException("Wrong usage of class identifier " + n.id,n.getLine());
		 return t;
	 }

	 ////////////////////////////////////////////////////////////////////////////////////////////////////////////TYPES
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
	 public TypeNode visitNode(ArrowTypeNode n) throws TypeException {
		 if (print) printNode(n);
		 for (Node par: n.parlist) visit(par);
		 visit(n.ret,"->"); //marks return type
		 return null;
	 }

	 @Override
	 public TypeNode visitNode(MethodTypeNode n) throws TypeException {// OO
		 if (print) printNode(n);
		 visit(n.fun);
		 return null;
	 }

	 @Override
	 public TypeNode visitNode(ClassTypeNode n) throws TypeException {// OO
		 if (print) printNode(n);
		 return null;
	 }

	 // STentry (return: field type): it is used to avoid null pointer exception
	 @Override
	 public TypeNode visitSTentry(STentry entry) throws TypeException {//thrown an incomplete exception; that will be catch
		 if (print) printSTentry("type");
		 return ckvisit(entry.type);
	 }

}
