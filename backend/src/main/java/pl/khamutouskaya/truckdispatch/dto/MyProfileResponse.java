package pl.khamutouskaya.truckdispatch.dto;

/**
 * Panel kierowcy: dane zalogowanego kierowcy i jego pojazd.
 *
 * @param vehicle pojazd kierowcy albo null, jeśli żaden nie jest przypisany
 */
public record MyProfileResponse(DriverResponse driver, VehicleResponse vehicle) {
}
