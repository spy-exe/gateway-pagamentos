export default function LinhaConta({
  rotulo,
  valor,
  peso
}: {
  rotulo: string;
  valor: string;
  peso?: "forte";
}) {
  return (
    <div className="linha-conta" data-peso={peso}>
      <span className="rotulo">{rotulo}</span>
      <span className="pontilhado" aria-hidden="true" />
      <span className="valor">{valor}</span>
    </div>
  );
}
