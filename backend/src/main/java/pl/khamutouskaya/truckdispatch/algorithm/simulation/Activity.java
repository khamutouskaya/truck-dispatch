package pl.khamutouskaya.truckdispatch.algorithm.simulation;

// Co kierowca robi w danej chwili
public enum Activity {
    DRIVING,
    BREAK,      // przerwa 45 min (561/2006)
    REST,       // odpoczynek dobowy 11 h (561/2006)
    LOADING,
    UNLOADING,
    FINISHED    // po rozładunku
}
