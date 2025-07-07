grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

CLASS : 'class' ;
INT : 'int' ;
BOOLEAN : 'boolean' ;
VOID: 'void' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;
IMPORT : 'import' ;
EXTENDS : 'extends' ;
STATIC : 'static' ;

INTEGER : [0] | ([1-9][0-9]*);
ID : [a-zA-Z_$]([a-zA-Z_0-9$])* ;

WS : [ \t\n\r\f]+ -> skip ;

EOL : '\r\n' | '\n' ;

SINGLE_COMMENT : '//' .*? (EOL | EOF) -> skip ;
MULTI_COMMENT : '/*' .*? '*/' -> skip ;

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT pkgName+= ID ( '.' pkgName+= ID )* ';'
    ;

classDecl
    : CLASS name= ID ( EXTENDS superName= ID )?
        '{'
        (varDecl |
        methodDecl)*
        '}'
    ;

varDecl
    : type name= ID ';'
    ;

type locals[boolean isArray=false, boolean isVarArg=false]
    : name= (INT | BOOLEAN | VOID | ID)
    (('[' ']' {$isArray=true;}) | ('...' {$isVarArg=true;}) )?
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false, boolean isMain=false]
    : (PUBLIC {$isPublic=true;})?
      (STATIC {$isStatic=true;})?
        type name= ID
        '(' ( param (',' param)* )? ')'
        '{' (varDecl | stmt)* '}'
    ;

param
    : type name= ID
    ;

stmt
    : '{' (stmt)* '}'                           #BracketStmt
    | IF '(' expr ')' stmt (ELSE stmt)?         #IfStmt
    | WHILE '(' expr ')' stmt                   #WhileStmt
    | expr '=' expr ';'                         #AssignStmt
    | RETURN expr ';'                           #ReturnStmt
    | expr ';'                                  #ExprStmt
    ;

// https://introcs.cs.princeton.edu/java/11precedence/
expr
    : '(' expr ')'                                      #Parenthesis
    | expr '[' expr ']'                                 #ArrayAccess
    | '[' ( expr ( ',' expr )* )? ']'                   #ArrayInit
    | 'new' type '[' expr ']'                           #ArrayCreation
    | 'new' name= ID '(' ')'                            #ObjectCreation
    | expr '.' name= ID '(' ( expr ( ',' expr )* )? ')' #MethodCall
    | expr '.length'                                    #LengthAccess
    | op='!' expr                                       #UnaryExpr
    | expr op=('*' | '/') expr                          #BinaryExpr
    | expr op=('+' | '-') expr                          #BinaryExpr
    | expr op=('<' | '>') expr                          #BinaryExpr
    | expr op='&&' expr                                 #LogicExpr
    | value= INTEGER                                    #IntegerLit
    | value='true'                                      #BooleanLit
    | value='false'                                     #BooleanLit
    | 'this'                                            #ThisRef
    | name= ID                                          #VarRefExpr
    ;
