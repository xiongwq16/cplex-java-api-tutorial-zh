package examples;

/* --------------------------------------------------------------------------
 * File: BendersATSP.java
 * Version 12.9.0  
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2001, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 *
 * Example BendersATSP.java solves a flow MILP model for an
 * Asymmetric Traveling Salesman Problem (ATSP) instance
 * through Benders decomposition.
 *
 * The arc costs of an ATSP instance are read from an input file.
 * The flow MILP model is decomposed into a master ILP and a worker LP.
 *
 * The master ILP is then solved by adding Benders' cuts during
 * the branch-and-cut process via the cut callback classes 
 * IloCplex.LazyConstraintCallback and IloCplex.UserCutCallback.
 * The cut callbacks add to the master ILP violated Benders' cuts
 * that are found by solving the worker LP.
 *
 * The example allows the user to decide if Benders' cuts have to be separated:
 *
 * a) Only to separate integer infeasible solutions.
 * In this case, Benders' cuts are treated as lazy constraints through the
 * class IloCplex.LazyConstraintCallback.
 *
 * b) Also to separate fractional infeasible solutions.
 * In this case, Benders' cuts are treated as lazy constraints through the
 * class IloCplex.LazyConstraintCallback.
 * In addition, Benders' cuts are also treated as user cuts through the
 * class IloCplex.UserCutCallback.
 *
 *
 * To run this example, command line arguments are required:
 *     java BendersATSP {0|1} [filename]
 * where
 *     0         Indicates that Benders' cuts are only used as lazy constraints,
 *               to separate integer infeasible solutions.
 *     1         Indicates that Benders' cuts are also used as user cuts,
 *               to separate fractional infeasible solutions.
 *
 *     filename  Is the name of the file containing the ATSP instance (arc costs).
 *               If filename is not specified, the instance
 *               ../../../examples/data/atsp.dat is read
 *
 *
 * ATSP instance defined on a directed graph G = (V, A)
 * - V = {0, ..., n-1}, V0 = V \ {0}
 * - A = {(i,j) : i in V, j in V, i != j }
 * - forall i in V: delta+(i) = {(i,j) in A : j in V}
 * - forall i in V: delta-(i) = {(j,i) in A : j in V}
 * - c(i,j) = traveling cost associated with (i,j) in A
 *
 * Flow MILP model
 *
 * Modeling variables:
 * forall (i,j) in A:
 *    x(i,j) = 1, if arc (i,j) is selected
 *           = 0, otherwise
 * forall k in V0, forall (i,j) in A:
 *    y(k,i,j) = flow of the commodity k through arc (i,j)
 *
 * Objective:
 * minimize sum((i,j) in A) c(i,j) * x(i,j)
 *
 * Degree constraints:
 * forall i in V: sum((i,j) in delta+(i)) x(i,j) = 1
 * forall i in V: sum((j,i) in delta-(i)) x(j,i) = 1
 *
 * Binary constraints on arc variables:
 * forall (i,j) in A: x(i,j) in {0, 1}
 *
 * Flow constraints:
 * forall k in V0, forall i in V:
 *    sum((i,j) in delta+(i)) y(k,i,j) - sum((j,i) in delta-(i)) y(k,j,i) = q(k,i)
 *    where q(k,i) =  1, if i = 0
 *                 = -1, if k == i
 *                 =  0, otherwise
 *
 * Capacity constraints:
 * forall k in V0, for all (i,j) in A: y(k,i,j) <= x(i,j)
 *
 * Nonnegativity of flow variables:
 * forall k in V0, for all (i,j) in A: y(k,i,j) >= 0
 */

import ilog.concert.*;
import ilog.cplex.*;

/**
 * Bender's Decomposition for ATSP .<br>
 * 
 * @author xiong
 * @version v1.0
 * @since JDK1.8
 */
public class BendersAtsp {

    /**
     * The class BendersLazyConsCallback .<br>
     * allows to add Benders' cuts as lazy constraints .<br>
     * 
     * @author xiong
     * @version v1.0
     * @since JDK1.8
     */
    public static class BendersLazyConsCallback extends IloCplex.LazyConstraintCallback {
        final IloIntVar[][] x;
        final WorkerLp workerLp;
        final int numNodes;

        BendersLazyConsCallback(IloIntVar[][] x, WorkerLp workerLp) {
            this.x = x;
            this.workerLp = workerLp;
            numNodes = x.length;
        }

        public void main() throws IloException {

            // Get the current x solution
            double[][] sol = new double[numNodes][];
            for (int i = 0; i < numNodes; ++i) {
                sol[i] = getValues(x[i]);
            }

            // Benders' cut separation
            IloRange cut = workerLp.separate(sol, x);
            if (cut != null) {
                add(cut, IloCplex.CutManagement.UseCutForce);
            }
        }
    }

    /**
     * The class BendersUserCutCallback .<br>
     * allows to add Benders' cuts as user cuts .<br>
     * 
     * @author xiong
     * @version v1.0
     * @since JDK1.8
     */
    public static class BendersUserCutCallback extends IloCplex.UserCutCallback {
        final IloIntVar[][] x;
        final WorkerLp workerLp;
        final int numNodes;

        BendersUserCutCallback(IloIntVar[][] x, WorkerLp workerLp) {
            this.x = x;
            this.workerLp = workerLp;
            numNodes = x.length;
        }

        public void main() throws IloException {

            // Skip the separation if not at the end of the cut loop
            if (!isAfterCutLoop()) {
                return;
            }

            // Get the current x solution
            double[][] sol = new double[numNodes][];
            for (int i = 0; i < numNodes; ++i) {
                sol[i] = getValues(x[i]);
            }

            // Benders' cut separation
            IloRange cut = workerLp.separate(sol, x);
            if (cut != null) {
                add(cut, IloCplex.CutManagement.UseCutForce);
            }
        }
    }

    /**
     * Data class to read an ATSP instance from an input file .<br>
     * 
     * @author xiong
     * @version v1.0
     * @since JDK1.8
     */
    static class Data {
        int numNodes;
        double[][] arcCost;

        Data(String fileName) throws IloException, java.io.IOException, InputDataReader.InputDataReaderException {
            InputDataReader reader = new InputDataReader(fileName);
            arcCost = reader.readDoubleArrayArray();
            numNodes = arcCost.length;
            for (int i = 0; i < numNodes; ++i) {
                if (arcCost[i].length != numNodes) {
                    throw new IloException("Inconsistent data in file " + fileName);
                }
                arcCost[i][i] = 0.;
            }
        }
    }

    /**
     * This class builds the worker LP (i.e., the dual of flow constraints and
     * capacity constraints of the flow MILP) and allows to separate violated .<br>
     * Benders' cuts .<br>
     * 
     * @author xiong
     * @version v1.0
     * @since JDK1.8
     */
    static class WorkerLp {
        IloCplex dualLpSolver;
        int numNodes;
        IloNumVar[][][] v;
        IloNumVar[][] u;
        IloObjective obj;
        
        
        /**
         * Create a Instance WorkerLp
         * The constructor sets up the IloCplex instance to solve the worker LP, 
         * and creates the worker LP (i.e., the dual of flow constraints and capacity 
         * constraints of the flow MILP)
         * 
         * Modeling variables:
         * forall k in V0, i in V:
         * u(k,i) = dual variable associated with flow constraint (k,i)
         * 
         * forall k in V0, forall (i,j) in A:
         * v(k,i,j) = dual variable associated with capacity constraint (k,i,j)
         * 
         * Objective:
         * minimize sum(k in V0) sum((i,j) in A) x(i,j) * v(k,i,j)
         * - sum(k in V0) u(k,0) + sum(k in V0) u(k,k)
         * 
         * Constraints:
         * forall k in V0, forall (i,j) in A:
         * u(k,i) - u(k,j) <= v(k,i,j)
         * 
         * Nonnegativity on variables v(k,i,j)
         * forall k in V0, forall (i,j) in A:
         * v(k,i,j) >= 0
         * 
         * @param numNodes
         * @throws IloException
         */
        WorkerLp(int numNodes) throws IloException {

            this.numNodes = numNodes;
            int i, j, k;

            // Set up IloCplex instance to solve the worker LP(dual problem)
            dualLpSolver = new IloCplex();
            dualLpSolver.setOut(null);

            // Turn off the presolve reductions and set the CPLEX optimizer
            dualLpSolver.setParam(IloCplex.Param.Preprocessing.Reduce, 0);
            // Solve the worker LP with primal simplex method
            dualLpSolver.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Primal);

            /*
             * Create variables v(k,i,j) forall k in V0, (i,j) in A.
             * For simplicity, also dummy variables v(k,i,i) are created.
             * Those variables are fixed to 0 and do not participate to the constraints.
             */
            v = new IloNumVar[numNodes - 1][numNodes][numNodes];
            for (k = 1; k < numNodes; ++k) {
                for (i = 0; i < numNodes; ++i) {
                    for (j = 0; j < numNodes; ++j) {
                        v[k - 1][i][j] = dualLpSolver.numVar(0., Double.MAX_VALUE, "v." + k + "." + i + "." + j);
                        dualLpSolver.add(v[k - 1][i][j]);
                    }
                    v[k - 1][i][i].setUB(0.);
                }
            }

            // Create variables u(k,i) forall k in V0, i in V
            u = new IloNumVar[numNodes - 1][numNodes];
            for (k = 1; k < numNodes; ++k) {
                for (i = 0; i < numNodes; ++i) {
                    u[k - 1][i] = dualLpSolver.numVar(-Double.MAX_VALUE, Double.MAX_VALUE, "u." + k + "." + i);
                    dualLpSolver.add(u[k - 1][i]);
                }
            }

            // Initial objective function is empty
            obj = dualLpSolver.addMinimize();

            /*
             * Add constraints:
             * forall k in V0, forall (i,j) in A:
             * u(k,i) - u(k,j) <= v(k,i,j)
             */
            for (k = 1; k < numNodes; ++k) {
                for (i = 0; i < numNodes; ++i) {
                    for (j = 0; j < numNodes; ++j) {
                        if (i != j) {
                            // 以linearNumExpr的形式添加约束，使用addTerm函数
                            IloLinearNumExpr expr = dualLpSolver.linearNumExpr();
                            expr.addTerm(v[k - 1][i][j], -1.);
                            expr.addTerm(u[k - 1][i], 1.);
                            expr.addTerm(u[k - 1][j], -1.);
                            dualLpSolver.addLe(expr, 0.);
                        }
                    }
                }
            }
        }
        
        void end() {
            dualLpSolver.end();
        }
        
        /**
         * This method separates Benders' cuts violated by the current x solution.
         * Violated cuts are found by solving the worker LP .<br>
         * 
         * @param xSol
         * @param x
         * @return 约束（割）
         * @throws IloException
         */
        IloRange separate(double[][] xSol, IloIntVar[][] x) throws IloException {

            int i;
            int j;
            int k;

            IloRange cut = null;

            /*
             * Update the objective function in the worker LP: 
             * minimize sum(k in V0) sum((i,j) in A) x(i,j) * v(k,i,j) 
             * - sum(k in V0) u(k,0) + sum(k in V0) u(k,k)
             */
            IloLinearNumExpr objExpr = dualLpSolver.linearNumExpr();
            for (k = 1; k < numNodes; ++k) {
                for (i = 0; i < numNodes; ++i) {
                    for (j = 0; j < numNodes; ++j) {
                        objExpr.addTerm(v[k - 1][i][j], xSol[i][j]);
                    }
                }
            }
            
            for (k = 1; k < numNodes; ++k) {
                objExpr.addTerm(u[k - 1][k], 1.);
                objExpr.addTerm(u[k - 1][0], -1.);
            }
            obj.setExpr(objExpr);

            // Solve the worker LP
            dualLpSolver.solve();

            // A violated cut is availableiff the solution(dual problem) status is Unbounded
            if (dualLpSolver.getStatus().equals(IloCplex.Status.Unbounded)) {
                // Get the violated cut as an unbounded ray of the worker LP
                IloLinearNumExpr rayExpr = dualLpSolver.getRay();

                /*
                 * Compute the cut from the unbounded ray. The cut is:
                 * sum((i,j) in A) (sum(k in V0) v(k,i,j)) * x(i,j)
                 * >= sum(k in V0) u(k,0) - u(k,k)
                 */
                IloLinearNumExpr cutLhs = dualLpSolver.linearNumExpr();
                double cutRhs = 0.;
                
                // 极射线的处理，迭代找寻极射线
                IloLinearNumExprIterator iter = rayExpr.linearIterator();

                // 根据变量在目标函数中的系数分析极射线对应约束的符号
                while (iter.hasNext()) {
                    IloNumVar var = iter.nextNumVar();
                    boolean varFound = false;
                    for (k = 1; k < numNodes && !varFound; ++k) {
                        for (i = 0; i < numNodes && !varFound; ++i) {
                            for (j = 0; j < numNodes && !varFound; ++j) {
                                if (var.equals(v[k - 1][i][j])) {
                                    cutLhs.addTerm(x[i][j], iter.getValue());
                                    varFound = true;
                                }
                            }
                        }
                    }
                    for (k = 1; k < numNodes && !varFound; ++k) {
                        for (i = 0; i < numNodes && !varFound; ++i) {
                            if (var.equals(u[k - 1][i])) {
                                if (i == 0) {
                                    cutRhs += iter.getValue();
                                } else if (i == k) {
                                    cutRhs -= iter.getValue();
                                }
                                varFound = true;
                            }
                        }
                    }
                }
                cut = dualLpSolver.ge(cutLhs, cutRhs);
            }
            return cut;
        }
    }
    
    /**
     * Create a Master Problem
     * This method creates the master ILP (arc variables x and degree constraints).
     * 
     * Modeling variables:
     * forall (i,j) in A: x(i,j) = 1, if arc (i,j) is selected = 0, otherwise
     * 
     * Objective:
     * minimize sum((i,j) in A) c(i,j) * x(i,j)
     * 
     * Degree constraints:
     * forall i in V:
     * sum((i,j) in delta+(i)) x(i,j) = 1
     * forall i in V:
     * sum((j,i) in delta-(i)) x(j,i) = 1
     * 
     * Binary constraints on arc variables:
     * forall (i,j) in A: 
     * x(i,j) in {0, 1} .<br>
     * 
     * @param model
     * @param data
     * @param x
     * @throws IloException
     */
    static void createMasterIlp(IloModeler model, Data data, IloIntVar[][] x) throws IloException {
        int i;
        int j;
        int numNodes = data.numNodes;

        /*
         * Create variables x(i,j) for (i,j) in A
         * For simplicity, also dummy variables x(i,i) are created
         * Those variables are fixed to 0 and do not participate to the constraints
         */
        for (i = 0; i < numNodes; ++i) {
            for (j = 0; j < numNodes; ++j) {
                x[i][j] = model.boolVar("x." + i + "." + j);
                model.add(x[i][j]);
            }
            x[i][i].setUB(0);
        }

        // Create objective function: minimize sum((i,j) in A ) c(i,j) * x(i,j)
        IloLinearNumExpr objExpr = model.linearNumExpr();
        for (i = 0; i < numNodes; ++i) {
            objExpr.add(model.scalProd(x[i], data.arcCost[i]));
        }
        model.addMinimize(objExpr);

        /*
         * Add the out degree constraints
         * forall i in V: sum((i,j) in delta+(i)) x(i,j) = 1
         */
        for (i = 0; i < numNodes; ++i) {
            IloLinearNumExpr expr = model.linearNumExpr();
            for (j = 0; j < i; ++j) {
                expr.addTerm(x[i][j], 1.);
            }
            for (j = i + 1; j < numNodes; ++j) {
                expr.addTerm(x[i][j], 1.);
            }
            model.addEq(expr, 1.);
        }
        
        /*
         * Add the in degree constraints.
         * forall i in V: sum((j,i) in delta-(i)) x(j,i) = 1
         */
        for (i = 0; i < numNodes; ++i) {
            IloLinearNumExpr expr = model.linearNumExpr();
            for (j = 0; j < i; ++j) {
                expr.addTerm(x[j][i], 1.);
            }
            for (j = i + 1; j < numNodes; ++j) {
                expr.addTerm(x[j][i], 1.);
            }
            model.addEq(expr, 1.);
        }

    }

    public static void main(String[] args) {
        System.out.println(args[0]);
        try {
            String fileName = "./data/atsp.dat";

            // Check the command line arguments
            if (args.length != 1 && args.length != 2) {
                usage();
                return;
            }

            if (!(args[0].equals("0") || args[0].equals("1"))) {
                usage();
                return;
            }

            boolean separateFracSols = (args[0].charAt(0) == '0' ? false : true);
            if (separateFracSols) {
                System.out.println("Benders' cuts separated to cut off: " + "Integer and fractional infeasible solutions.");
            } else {
                System.out.println("Benders' cuts separated to cut off: " + "Only integer infeasible solutions.");
            }

            if (args.length == 2) {
                fileName = args[1];
            }

            // Read arc_costs from data file (9 city problem)
            Data data = new Data(fileName);

            // create master ILP
            int numNodes = data.numNodes;
            IloCplex milpSolver = new IloCplex();
            IloIntVar[][] x = new IloIntVar[numNodes][numNodes];
            createMasterIlp(milpSolver, data, x);

            // Create workerLP for Benders' cuts separation
            WorkerLp workerLp = new WorkerLp(numNodes);
            
            // Set up the cut callback to be used for separating Benders' cuts
            milpSolver.setParam(IloCplex.Param.Preprocessing.Presolve, false);

            /*
             * Set the maximum number of threads to 1. This instruction is redundant: 
             * If MIP control callbacks are registered, then by default CPLEX uses 1 (one) thread only.
             * Note that the current example may not work properly if more than 1 threads are used, 
             * because the callback functions modify shared global data.
             * We refer the user to the documentation to see how to deal with
             * multi-thread runs in presence of MIP control callbacks.
             */
            milpSolver.setParam(IloCplex.Param.Threads, 1);
            
            // Turn on traditional search for use with control callbacks
            milpSolver.setParam(IloCplex.Param.MIP.Strategy.Search, IloCplex.MIPSearch.Traditional);

            milpSolver.use(new BendersLazyConsCallback(x, workerLp));
            if (separateFracSols) {
                milpSolver.use(new BendersUserCutCallback(x, workerLp));
            }

            // Solve the model and write out the solution
            if (milpSolver.solve()) {
                System.out.println();
                System.out.println("Solution status: " + milpSolver.getStatus());
                System.out.println("Objective value: " + milpSolver.getObjValue());
                
                if (milpSolver.getStatus().equals(IloCplex.Status.Optimal)) {
                    // Write out the optimal tour
                    int i, j;
                    double[][] sol = new double[numNodes][];
                    int[] succ = new int[numNodes];
                    for (j = 0; j < numNodes; ++j) {
                        succ[j] = -1;
                    }

                    for (i = 0; i < numNodes; ++i) {
                        sol[i] = milpSolver.getValues(x[i]);
                        for (j = 0; j < numNodes; ++j) {
                            if (sol[i][j] > 1e-03) {
                                succ[i] = j;
                            }
                        }
                    }

                    System.out.println("Optimal tour:");
                    i = 0;
                    while (succ[i] != 0) {
                        System.out.print(i + ", ");
                        i = succ[i];
                    }
                    System.out.println(i);
                } else {
                    System.out.println("Solution status is not Optimal");
                }
            } else {
                System.out.println("No solution available");
            }

            workerLp.end();
            milpSolver.end();
        } catch (IloException ex) {
            System.out.println("Concert Error: " + ex);
        } catch (InputDataReader.InputDataReaderException ex) {
            System.out.println("Data Error: " + ex);
        } catch (java.io.IOException ex) {
            System.out.println("IO Error: " + ex);
        }
    }

    static void usage() {
        System.out.println("Usage:     java BendersATSP {0|1} [filename]");
        System.out.println(" 0:        Benders' cuts only used as lazy constraints,");
        System.out.println("           to separate integer infeasible solutions.");
        System.out.println(" 1:        Benders' cuts also used as user cuts,");
        System.out.println("           to separate fractional infeasible solutions.");
        System.out.println(" filename: ATSP instance file name.");
        System.out.println("           File ../../../examples/data/atsp.dat used " + "if no name is provided.");
    }
}
