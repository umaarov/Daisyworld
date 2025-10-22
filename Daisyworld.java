import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;

public class Daisyworld {

    // Grid size
    public static final int GRID_WIDTH = 100;
    public static final int GRID_HEIGHT = 100;
    public static final int CELL_SIZE = 6; // Size of each daisy cell in pixels

    // Simulation Ticker (milliseconds)
    public static final int SIM_SPEED_MS = 5; // How fast the simulation runs

    // Daisy Albedos (reflectivity)
    public static final double ALBEDO_EMPTY = 0.4;  // Reflectivity of empty ground
    public static final double ALBEDO_WHITE = 0.75; // Reflectivity of white daisies
    public static final double ALBEDO_BLACK = 0.25; // Reflectivity of black daisies
    
    // Solar Luminosity (Sun's brightness)
    public static final double STARTING_LUMINOSITY = 0.8;
    public static final double MAX_LUMINOSITY = 1.6;
    public static final double LUMINOSITY_INCREASE = 0.0001; // How fast the sun gets hotter
    
    // --- OUR HIDDEN FACTOR ---
    // This is your unique "Pollen Effect"
    public static final double POLLEN_CONVERSION_CHANCE = 0.05; // 5% chance to convert a neighbor

    // --- Global State ---
    // These variables will be updated by the simulation.
    public static double globalLuminosity = STARTING_LUMINOSITY;
    public static double globalTemperature = 0.0;
    public static int blackDaisyCount = 0;
    public static int whiteDaisyCount = 0;
    public static int emptyPatchCount = 0;
    public static int tickCount = 0;
    
    // Main window
    private static JFrame mainFrame;
    // Labels to show data
    private static JLabel tempLabel;
    private static JLabel sunLabel;
    private static JLabel blackLabel;
    private static JLabel whiteLabel;
    private static JLabel tickLabel;

    /**
     * The Patch class represents a single square on our planet.
     * It can either be empty, or have a black or white daisy.
     */
    static class Patch {
        enum PatchType {
            EMPTY,
            BLACK_DAISY,
            WHITE_DAISY
        }
        
        PatchType type;

        public Patch(PatchType type) {
            this.type = type;
        }

        // Gets the color for this patch to be drawn on screen
        public Color getColor() {
            switch (type) {
                case BLACK_DAISY: return Color.BLACK;
                case WHITE_DAISY: return Color.WHITE;
                case EMPTY:
                default:
                    return Color.GRAY; // Barren land
            }
        }

        // Gets the albedo (reflectivity) for this patch
        public double getAlbedo() {
            switch (type) {
                case BLACK_DAISY: return ALBEDO_BLACK;
                case WHITE_DAISY: return ALBEDO_WHITE;
                case EMPTY:
                default:
                    return ALBEDO_EMPTY;
            }
        }
    }


    /**
     * The SimulationPanel is the visual grid where all the action happens.
     * It extends JPanel and draws the daisies.
     * It also contains the main simulation loop (the Timer).
     */
    static class SimulationPanel extends JPanel implements ActionListener {

        private Patch[][] grid;
        private Timer simulationTimer;
        private Random random = new Random();

        public SimulationPanel() {
            // Set up the grid
            grid = new Patch[GRID_WIDTH][GRID_HEIGHT];
            initializeGrid();

            // Set the panel size
            setPreferredSize(new Dimension(GRID_WIDTH * CELL_SIZE, GRID_HEIGHT * CELL_SIZE));
            setBackground(Color.DARK_GRAY);

            // Start the simulation timer
            // The timer will call the "actionPerformed" method every SIM_SPEED_MS milliseconds
            simulationTimer = new Timer(SIM_SPEED_MS, this);
            simulationTimer.start();
        }

        /**
         * Fills the grid with empty patches and some random daisies to start.
         */
        private void initializeGrid() {
            emptyPatchCount = 0;
            blackDaisyCount = 0;
            whiteDaisyCount = 0;

            for (int x = 0; x < GRID_WIDTH; x++) {
                for (int y = 0; y < GRID_HEIGHT; y++) {
                    // Start with 60% empty, 20% black, 20% white
                    double r = random.nextDouble();
                    if (r < 0.2) {
                        grid[x][y] = new Patch(Patch.PatchType.BLACK_DAISY);
                        blackDaisyCount++;
                    } else if (r < 0.4) {
                        grid[x][y] = new Patch(Patch.PatchType.WHITE_DAISY);
                        whiteDaisyCount++;
                    } else {
                        grid[x][y] = new Patch(Patch.PatchType.EMPTY);
                        emptyPatchCount++;
                    }
                }
            }
            // Ensure total count is correct
            emptyPatchCount = (GRID_WIDTH * GRID_HEIGHT) - blackDaisyCount - whiteDaisyCount;
        }

        /**
         * This is the MAIN SIMULATION LOOP.
         * It's called by the Timer every tick.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            // 1. Update the sun's brightness
            updateSun();

            // 2. Calculate the planet's temperature
            calculateGlobalTemperature();

            // 3. Run our UNIQUE Pollen Conversion
            runPollenConversion();

            // 4. Run the classic reproduction and death
            runReproductionAndDeath();

            // 5. Update the data labels
            updateDataLabels();
            
            // 6. Redraw the screen
            repaint();
            
            tickCount++;
        }

        /**
         * Slowly increases the sun's luminosity over time.
         */
        private void updateSun() {
            if (globalLuminosity < MAX_LUMINOSITY) {
                globalLuminosity += LUMINOSITY_INCREASE;
            }
        }

        /**
         * Calculates the planet's albedo and new temperature.
         */
        private void calculateGlobalTemperature() {
            // 1. Calculate total albedo (average reflectivity)
            double totalAlbedo = (whiteDaisyCount * ALBEDO_WHITE) +
                                 (blackDaisyCount * ALBEDO_BLACK) +
                                 (emptyPatchCount * ALBEDO_EMPTY);
            double averageAlbedo = totalAlbedo / (GRID_WIDTH * GRID_HEIGHT);

            // 2. Calculate new temperature
            // This is a simplified physics formula.
            // A hotter sun (luminosity) and lower albedo (less reflective) = hotter planet.
            double absorbedLuminosity = globalLuminosity * (1 - averageAlbedo);
            double planetaryTemp;
            if (absorbedLuminosity > 0) {
                // Stefan-Boltzmann law simplification
                planetaryTemp = Math.pow(absorbedLuminosity, 0.25) * 50.0 - 25.0; // Scaled for effect
            } else {
                planetaryTemp = -273.0; // Absolute zero
            }
            globalTemperature = planetaryTemp;
        }

        
        // ####################################################################
        // #                                                                  #
        // #           "POLLEN EFFECT" LOGIC                                  #
        // #                                                                  #
        // ####################################################################
        private void runPollenConversion() {
            // We must create a temporary "next step" grid.
            // If we change the main grid directly, a newly converted daisy
            // could convert its neighbor in the *same tick*, which isn't fair.
            Patch[][] nextGrid = new Patch[GRID_WIDTH][GRID_HEIGHT];
            // Initialize nextGrid with current state
            for(int x = 0; x < GRID_WIDTH; x++) {
                for(int y = 0; y < GRID_HEIGHT; y++) {
                    nextGrid[x][y] = new Patch(grid[x][y].type);
                }
            }

            // Loop through every patch on the *current* grid
            for (int x = 0; x < GRID_WIDTH; x++) {
                for (int y = 0; y < GRID_HEIGHT; y++) {
                    
                    Patch currentPatch = grid[x][y];
                    if (currentPatch.type == Patch.PatchType.EMPTY) {
                        continue; // Empty patches can't spread pollen
                    }
                    
                    // This patch is a daisy. Check its 8 neighbors.
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue; // Skip self

                            int neighborX = (x + dx + GRID_WIDTH) % GRID_WIDTH;   // Wraparound edges
                            int neighborY = (y + dy + GRID_HEIGHT) % GRID_HEIGHT; // Wraparound edges

                            Patch neighborPatch = grid[neighborX][neighborY];

                            // Check for an "enemy" daisy
                            boolean isEnemy = (currentPatch.type == Patch.PatchType.BLACK_DAISY &&
                                               neighborPatch.type == Patch.PatchType.WHITE_DAISY) ||
                                              (currentPatch.type == Patch.PatchType.WHITE_DAISY &&
                                               neighborPatch.type == Patch.PatchType.BLACK_DAISY);

                            if (isEnemy) {
                                // Roll the dice!
                                if (random.nextDouble() < POLLEN_CONVERSION_CHANCE) {
                                    // SUCCESS! Convert the neighbor in the *next* grid.
                                    // The neighbor is converted to the *current* patch's type.
                                    nextGrid[neighborX][neighborY].type = currentPatch.type;
                                }
                            }
                        }
                    }
                }
            }

            // Now, swap the main grid with our new one
            grid = nextGrid;
        }


        /**
         * This is the classic Daisyworld logic.
         * Daisies check if they should die (too hot/cold)
         * Empty patches check if a new daisy should grow.
         */
        private void runReproductionAndDeath() {
            // Again, we use a "next step" grid to make changes
            Patch[][] nextGrid = new Patch[GRID_WIDTH][GRID_HEIGHT];
            for(int x = 0; x < GRID_WIDTH; x++) {
                for(int y = 0; y < GRID_HEIGHT; y++) {
                    nextGrid[x][y] = new Patch(grid[x][y].type);
                }
            }

            // Reset counts, we will recalculate them
            blackDaisyCount = 0;
            whiteDaisyCount = 0;
            emptyPatchCount = 0;

            for (int x = 0; x < GRID_WIDTH; x++) {
                for (int y = 0; y < GRID_HEIGHT; y++) {
                    
                    Patch currentPatch = grid[x][y];
                    
                    // We just use global temperature for simplicity
                    // A more complex model would calculate local temperature
                    double localTemp = globalTemperature; 
                    
                    // Calculate the "growth probability" for this patch
                    // This is a parabola (upside-down U shape)
                    // Optimal temp for black is 10, for white is 30.
                    double blackGrowthProb = calculateGrowthProb(localTemp, 10.0, 5.0, 40.0);
                    double whiteGrowthProb = calculateGrowthProb(localTemp, 30.0, 5.0, 40.0);

                    // --- Logic for EMPTY patches ---
                    if (currentPatch.type == Patch.PatchType.EMPTY) {
                        // Check for seeding
                        double seedChance = random.nextDouble();
                        if (seedChance < blackGrowthProb) {
                            nextGrid[x][y].type = Patch.PatchType.BLACK_DAISY;
                        } else if (seedChance < (blackGrowthProb + whiteGrowthProb)) {
                            // Note: This logic slightly favors black. A better way
                            // would be to check neighbors, but this is simpler.
                            nextGrid[x][y].type = Patch.PatchType.WHITE_DAISY;
                        }
                    } 
                    // --- Logic for DAISY patches (check for death) ---
                    else {
                        double deathChance = 0.0;
                        if (currentPatch.type == Patch.PatchType.BLACK_DAISY) {
                            if (blackGrowthProb <= 0.01) deathChance = 0.3; // High chance of death if temp is bad
                        } else {
                            if (whiteGrowthProb <= 0.01) deathChance = 0.3; // High chance of death
                        }

                        if (random.nextDouble() < deathChance) {
                            nextGrid[x][y].type = Patch.PatchType.EMPTY; // Daisy dies
                        }
                    }
                    
                    // Update counts based on the *new* grid
                    switch(nextGrid[x][y].type) {
                        case BLACK_DAISY: blackDaisyCount++; break;
                        case WHITE_DAISY: whiteDaisyCount++; break;
                        case EMPTY: emptyPatchCount++; break;
                    }
                }
            }
            
            // Swap to the new grid
            grid = nextGrid;
        }

        /**
         * Calculates the probability of a daisy growing at a given temperature.
         * Uses a parabolic curve.
         * @param temp The current temperature.
         * @param optimalTemp The "perfect" temperature for this daisy.
         * @param minTemp The minimum temperature for survival.
         * @param maxTemp The maximum temperature for survival.
         */
        private double calculateGrowthProb(double temp, double optimalTemp, double minTemp, double maxTemp) {
            if (temp < minTemp || temp > maxTemp) {
                return 0.0; // Outside survival range
            }
            
            // Parabola formula: 1 - k * (temp - optimalTemp)^2
            double range = maxTemp - minTemp;
            double k = 4.0 / (range * range); // Scaling factor
            double prob = 1.0 - k * Math.pow(temp - optimalTemp, 2);
            
            return Math.max(0, prob); // Ensure probability is not negative
        }

        /**
         * This method draws the grid on the screen.
         * It's called automatically by repaint().
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); // Clears the screen

            // Loop through the grid and draw each cell
            for (int x = 0; x < GRID_WIDTH; x++) {
                for (int y = 0; y < GRID_HEIGHT; y++) {
                    g.setColor(grid[x][y].getColor());
                    g.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }
            }
        }
    }


    /**
     * Main method to start the program.
     */
    public static void main(String[] args) {
        // We use SwingUtilities.invokeLater to make sure the GUI
        // is created on the correct thread.
        SwingUtilities.invokeLater(() -> {
            mainFrame = new JFrame("Daisyworld - The Pollen Effect");
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.setResizable(false);
            
            // Main container
            JPanel mainPanel = new JPanel(new BorderLayout());

            // 1. The Simulation Panel (the grid)
            SimulationPanel simPanel = new SimulationPanel();
            mainPanel.add(simPanel, BorderLayout.CENTER);

            // 2. The Control Panel (the data)
            JPanel controlPanel = new JPanel();
            controlPanel.setLayout(new GridLayout(5, 1)); // 5 rows, 1 column
            controlPanel.setBackground(Color.LIGHT_GRAY);

            // --- Create the data labels ---
            tickLabel = new JLabel(" Tick: 0");
            tickLabel.setFont(new Font("Arial", Font.BOLD, 14));
            
            tempLabel = new JLabel(" Global Temp: 0.0 °C");
            tempLabel.setFont(new Font("Arial", Font.BOLD, 14));

            sunLabel = new JLabel(" Luminosity: " + String.format("%.2f", STARTING_LUMINOSITY));
            sunLabel.setFont(new Font("Arial", Font.BOLD, 14));

            blackLabel = new JLabel(" Black Daisies: " + blackDaisyCount);
            blackLabel.setFont(new Font("Arial", Font.BOLD, 14));
            
            whiteLabel = new JLabel(" White Daisies: " + whiteDaisyCount);
            whiteLabel.setFont(new Font("Arial", Font.BOLD, 14));

            // Add labels to the panel
            controlPanel.add(tickLabel);
            controlPanel.add(tempLabel);
            controlPanel.add(sunLabel);
            controlPanel.add(blackLabel);
            controlPanel.add(whiteLabel);
            
            mainPanel.add(controlPanel, BorderLayout.EAST);

            // Finish setting up the window
            mainFrame.setContentPane(mainPanel);
            mainFrame.pack(); // Sizes the window to fit the panels
            mainFrame.setLocationRelativeTo(null); // Center on screen
            mainFrame.setVisible(true);
        });
    }

    /**
     * This helper method is called by the simulation loop to update
     * the text labels in the GUI.
     */
    private static void updateDataLabels() {
        tickLabel.setText(" Tick: " + tickCount);
        tempLabel.setText(String.format(" Global Temp: %.2f °C", globalTemperature));
        sunLabel.setText(String.format(" Luminosity: %.2f", globalLuminosity));
        blackLabel.setText(" Black Daisies: " + blackDaisyCount);
        whiteLabel.setText(" White Daisies: " + whiteDaisyCount);
    }
}